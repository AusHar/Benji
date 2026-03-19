package com.austinharlan.trading_dashboard.marketdata;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.xml.sax.InputSource;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

@Component
@Profile("!dev")
public class YahooFinanceMarketDataProvider implements MarketDataProvider {

  private static final Logger log = LoggerFactory.getLogger(YahooFinanceMarketDataProvider.class);
  private static final String USER_AGENT = "Mozilla/5.0 (compatible; trading-dashboard/1.0)";
  private static final Set<String> NULL_MARKERS = Set.of("none", "-", "null", "");

  private final WebClient query1Client;
  private final WebClient query2Client;
  private final WebClient rssClient;
  private final YahooCrumbProvider crumbProvider;
  private final MarketDataQuotaTracker quotaTracker;
  private final MarketDataProperties properties;
  private final boolean retryEnabled;
  private final int maxAttempts;
  private final RetryBackoffSpec baseRetrySpec;

  public YahooFinanceMarketDataProvider(
      WebClient.Builder builder,
      MarketDataProperties properties,
      MarketDataQuotaTracker quotaTracker,
      YahooCrumbProvider crumbProvider,
      Environment environment) {
    this.properties = Objects.requireNonNull(properties);
    this.quotaTracker = Objects.requireNonNull(quotaTracker);
    this.crumbProvider = Objects.requireNonNull(crumbProvider);

    HttpClient httpClient =
        HttpClient.create()
            .option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                (int) properties.getConnectTimeout().toMillis())
            .responseTimeout(properties.getReadTimeout())
            .doOnConnected(
                conn ->
                    conn.addHandlerLast(
                            new ReadTimeoutHandler(
                                properties.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(
                            new WriteTimeoutHandler(
                                properties.getWriteTimeout().toMillis(), TimeUnit.MILLISECONDS)));
    ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

    this.query1Client =
        builder
            .baseUrl(normalizeUrl(properties.getBaseUrl()))
            .clientConnector(connector)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
            .build();

    this.query2Client =
        WebClient.builder()
            .baseUrl(normalizeUrl(properties.getQuery2BaseUrl()))
            .clientConnector(connector)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
            .build();

    this.rssClient =
        WebClient.builder()
            .baseUrl(normalizeUrl(properties.getYahooRssBaseUrl()))
            .clientConnector(connector)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
            .build();

    this.retryEnabled =
        Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.equalsIgnoreCase("prod"));
    this.maxAttempts = Math.max(1, properties.getRetry().getMaxAttempts());
    this.baseRetrySpec =
        maxAttempts > 1
            ? Retry.backoff(maxAttempts - 1, properties.getRetry().getInitialBackoff())
                .maxBackoff(properties.getRetry().getMaxBackoff())
                .filter(
                    t ->
                        t instanceof MarketDataClientException
                            && !(t instanceof MarketDataRateLimitException))
                .onRetryExhaustedThrow((spec, signal) -> propagateFinalFailure(signal.failure()))
            : null;
  }

  @Override
  public Quote getQuote(String symbol) {
    requireSymbol(symbol);
    quotaTracker.increment();
    JsonNode root =
        withRetry(fetchQuoteMono(symbol), "quote", symbol).block(properties.getReadTimeout());
    return toQuote(symbol, root);
  }

  @Override
  public List<DailyBar> getDailyHistory(String symbol) {
    requireSymbol(symbol);
    quotaTracker.increment();
    JsonNode root =
        withRetry(
                query1Client
                    .get()
                    .uri(
                        u ->
                            u.path("/v8/finance/chart/{symbol}")
                                .queryParam("range", "3mo")
                                .queryParam("interval", "1d")
                                .build(symbol))
                    .retrieve()
                    .onStatus(
                        s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(
                                    b ->
                                        new MarketDataRateLimitException(
                                            "Yahoo Finance rate limit reached")))
                    .onStatus(
                        HttpStatusCode::isError,
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(
                                    b ->
                                        new MarketDataClientException(
                                            "Yahoo Finance error " + r.statusCode() + ": " + b)))
                    .bodyToMono(JsonNode.class)
                    .onErrorMap(
                        WebClientResponseException.class,
                        ex ->
                            new MarketDataClientException(
                                "Yahoo Finance call failed: " + ex.getStatusCode(), ex))
                    .onErrorMap(
                        WebClientRequestException.class,
                        ex ->
                            new MarketDataClientException(
                                "Yahoo Finance request failed: " + ex.getMessage(), ex)),
                "history",
                symbol)
            .block(properties.getReadTimeout());
    return toHistory(symbol, root);
  }

  @Override
  public CompanyOverview getOverview(String symbol) {
    requireSymbol(symbol);
    quotaTracker.increment();
    try {
      return fetchOverviewOnce(symbol, crumbProvider.getCrumb());
    } catch (MarketDataClientException ex) {
      if (ex.getMessage() != null && ex.getMessage().contains("Yahoo Finance 401")) {
        log.warn("quoteSummary returned 401 for {}; invalidating crumb and retrying", symbol);
        crumbProvider.invalidateCrumb();
        return fetchOverviewOnce(symbol, crumbProvider.getCrumb());
      }
      throw ex;
    }
  }

  @Override
  public List<NewsArticle> getNews(String symbol) {
    requireSymbol(symbol);
    quotaTracker.increment();
    String xml =
        rssClient
            .get()
            .uri(u -> u.path("/rss/headline").queryParam("s", symbol).build())
            .retrieve()
            .onStatus(HttpStatusCode::isError, r -> Mono.empty())
            .bodyToMono(String.class)
            .onErrorReturn("")
            .block(properties.getReadTimeout());
    return parseRss(xml);
  }

  // ── Private helpers ─────────────────────────────────────────────────────────

  private Mono<JsonNode> fetchQuoteMono(String symbol) {
    return query1Client
        .get()
        .uri(u -> u.path("/v7/finance/quote").queryParam("symbols", symbol).build())
        .retrieve()
        .onStatus(
            s -> s.value() == HttpStatus.NOT_FOUND.value(),
            r ->
                r.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(b -> new QuoteNotFoundException("Quote not found for " + symbol)))
        .onStatus(
            s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
            r ->
                r.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(b -> new MarketDataRateLimitException("Yahoo Finance rate limit reached")))
        .onStatus(
            HttpStatusCode::isError,
            r ->
                r.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(
                        b ->
                            new MarketDataClientException(
                                "Yahoo Finance error " + r.statusCode() + ": " + b)))
        .bodyToMono(JsonNode.class)
        .onErrorMap(
            WebClientResponseException.class,
            ex ->
                new MarketDataClientException(
                    "Yahoo Finance call failed: " + ex.getStatusCode(), ex))
        .onErrorMap(
            WebClientRequestException.class,
            ex ->
                new MarketDataClientException(
                    "Yahoo Finance request failed: " + ex.getMessage(), ex));
  }

  private CompanyOverview fetchOverviewOnce(String symbol, String crumb) {
    JsonNode root =
        query2Client
            .get()
            .uri(
                u ->
                    u.path("/v10/finance/quoteSummary/{symbol}")
                        .queryParam("crumb", crumb)
                        .queryParam(
                            "modules", "price,summaryProfile,summaryDetail,defaultKeyStatistics")
                        .build(symbol))
            .retrieve()
            .onStatus(
                s -> s.value() == HttpStatus.UNAUTHORIZED.value(),
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(
                            b ->
                                new MarketDataClientException(
                                    "Yahoo Finance 401 on quoteSummary for " + symbol)))
            .onStatus(
                s -> s.value() == HttpStatus.NOT_FOUND.value(),
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new QuoteNotFoundException("Overview not found for " + symbol)))
            .onStatus(
                s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(
                            b ->
                                new MarketDataRateLimitException(
                                    "Yahoo Finance rate limit reached")))
            .onStatus(
                HttpStatusCode::isError,
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(
                            b ->
                                new MarketDataClientException(
                                    "Yahoo Finance error " + r.statusCode() + ": " + b)))
            .bodyToMono(JsonNode.class)
            .block(properties.getReadTimeout());
    return toOverview(symbol, root);
  }

  private <T> Mono<T> withRetry(Mono<T> mono, String action, String symbol) {
    if (!retryEnabled || baseRetrySpec == null) return mono;
    return mono.retryWhen(
        baseRetrySpec.doBeforeRetry(
            s ->
                log.warn(
                    "Retrying Yahoo Finance {} for {} after attempt {}: {}",
                    action,
                    symbol,
                    s.totalRetries(),
                    s.failure().getMessage())));
  }

  private RuntimeException propagateFinalFailure(Throwable failure) {
    if (failure instanceof RuntimeException r) return r;
    return new MarketDataClientException("Yahoo Finance retries exhausted", failure);
  }

  // ── Parsing ─────────────────────────────────────────────────────────────────

  private Quote toQuote(String symbol, JsonNode root) {
    JsonNode result = root == null ? null : root.path("quoteResponse").path("result");
    if (result == null || !result.isArray() || result.isEmpty()) {
      throw new QuoteNotFoundException("Quote not found for " + symbol);
    }
    JsonNode item = result.get(0);
    JsonNode priceNode = item.path("regularMarketPrice");
    if (priceNode.isMissingNode() || priceNode.isNull() || priceNode.asDouble(0) == 0) {
      throw new QuoteNotFoundException("Quote not found for " + symbol);
    }
    BigDecimal price = BigDecimal.valueOf(priceNode.asDouble());
    JsonNode cpNode = item.path("regularMarketChangePercent");
    BigDecimal changePercent =
        (!cpNode.isMissingNode() && !cpNode.isNull())
            ? BigDecimal.valueOf(cpNode.asDouble())
            : null;
    long epochSeconds = item.path("regularMarketTime").asLong(0);
    Instant timestamp = epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : Instant.now();
    return new Quote(symbol, price, changePercent, timestamp);
  }

  private List<DailyBar> toHistory(String symbol, JsonNode root) {
    JsonNode result = root == null ? null : root.path("chart").path("result");
    if (result == null || !result.isArray() || result.isEmpty()) {
      throw new QuoteNotFoundException("History not found for " + symbol);
    }
    JsonNode item = result.get(0);
    JsonNode timestamps = item.path("timestamp");
    JsonNode quoteArr = item.path("indicators").path("quote");
    if (!timestamps.isArray()
        || timestamps.isEmpty()
        || !quoteArr.isArray()
        || quoteArr.isEmpty()) {
      throw new QuoteNotFoundException("History not found for " + symbol);
    }
    JsonNode q = quoteArr.get(0);
    List<DailyBar> bars = new ArrayList<>();
    for (int i = 0; i < timestamps.size(); i++) {
      try {
        LocalDate date =
            Instant.ofEpochSecond(timestamps.get(i).asLong()).atZone(ZoneOffset.UTC).toLocalDate();
        bars.add(
            new DailyBar(
                date,
                bigDecimalAt(q.path("open"), i),
                bigDecimalAt(q.path("high"), i),
                bigDecimalAt(q.path("low"), i),
                bigDecimalAt(q.path("close"), i),
                q.path("volume").has(i) ? q.path("volume").get(i).asLong(0) : 0L));
      } catch (Exception ex) {
        log.warn("Skipping malformed candle at index {}", i, ex);
      }
    }
    bars.sort(Comparator.comparing(DailyBar::date));
    return bars;
  }

  private CompanyOverview toOverview(String symbol, JsonNode root) {
    JsonNode result = root == null ? null : root.path("quoteSummary").path("result");
    if (result == null || !result.isArray() || result.isEmpty()) {
      throw new QuoteNotFoundException("Overview not found for " + symbol);
    }
    JsonNode item = result.get(0);
    JsonNode price = item.path("price");
    JsonNode profile = item.path("summaryProfile");
    JsonNode detail = item.path("summaryDetail");
    JsonNode stats = item.path("defaultKeyStatistics");

    String name = safeText(price, "shortName");
    if (name == null) name = safeText(price, "longName");
    String sector = safeText(profile, "sector");
    BigDecimal marketCap = safeRaw(price, "marketCap");
    BigDecimal pe = safeRaw(detail, "trailingPE");
    BigDecimal dividendYield = safeRaw(detail, "dividendYield");
    BigDecimal beta = safeRaw(stats, "beta");
    BigDecimal eps = safeRaw(stats, "trailingEps");
    BigDecimal high52 = safeRaw(stats, "fiftyTwoWeekHigh");
    BigDecimal low52 = safeRaw(stats, "fiftyTwoWeekLow");
    return new CompanyOverview(
        symbol, name, sector, null, marketCap, pe, eps, dividendYield, beta, high52, low52);
  }

  private List<NewsArticle> parseRss(String xml) {
    if (xml == null || xml.isBlank()) return List.of();
    try {
      SyndFeed feed = new SyndFeedInput().build(new InputSource(new StringReader(xml)));
      List<NewsArticle> articles = new ArrayList<>();
      for (SyndEntry e : feed.getEntries()) {
        try {
          String headline = e.getTitle();
          String url = e.getLink();
          if (headline == null || url == null) continue;
          String summary = e.getDescription() != null ? e.getDescription().getValue() : null;
          String source = e.getSource() != null ? e.getSource().getTitle() : null;
          Instant publishedAt =
              e.getPublishedDate() != null ? e.getPublishedDate().toInstant() : Instant.now();
          long id = (long) Math.abs(Objects.hashCode(e.getUri()));
          articles.add(new NewsArticle(id, headline, summary, source, url, null, publishedAt));
        } catch (Exception ex) {
          log.warn("Skipping malformed RSS entry", ex);
        }
      }
      return articles.stream()
          .sorted(Comparator.comparing(NewsArticle::publishedAt).reversed())
          .limit(10)
          .toList();
    } catch (Exception ex) {
      log.warn("Failed to parse RSS feed: {}", ex.getMessage());
      return List.of();
    }
  }

  private static BigDecimal bigDecimalAt(JsonNode arr, int i) {
    if (!arr.isArray() || !arr.has(i) || arr.get(i).isNull()) return BigDecimal.ZERO;
    return BigDecimal.valueOf(arr.get(i).asDouble());
  }

  private static BigDecimal safeRaw(JsonNode parent, String field) {
    JsonNode node = parent.path(field);
    if (node.isMissingNode() || node.isNull()) return null;
    JsonNode raw = node.path("raw");
    if (raw.isMissingNode() || raw.isNull()) return null;
    return raw.isNumber() ? BigDecimal.valueOf(raw.asDouble()) : null;
  }

  private static String safeText(JsonNode node, String field) {
    if (node == null || node.isMissingNode()) return null;
    JsonNode raw = node.get(field);
    if (raw == null || raw.isNull()) return null;
    String val = raw.asText(null);
    if (val == null) return null;
    val = val.trim();
    return NULL_MARKERS.contains(val.toLowerCase(Locale.ROOT)) ? null : val;
  }

  private static void requireSymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
  }

  private static String normalizeUrl(String url) {
    if (url == null) throw new MarketDataClientException("URL must not be null");
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
