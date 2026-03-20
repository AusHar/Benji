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
import java.util.Objects;
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
public class CoinGeckoMarketDataProvider implements MarketDataProvider {

  private static final Logger log = LoggerFactory.getLogger(CoinGeckoMarketDataProvider.class);
  private static final String USER_AGENT = "Mozilla/5.0 (compatible; trading-dashboard/1.0)";

  private final WebClient coinGeckoClient;
  private final WebClient rssClient;
  private final CryptoSymbolMapper mapper;
  private final MarketDataQuotaTracker quotaTracker;
  private final MarketDataProperties properties;
  private final boolean retryEnabled;
  private final int maxAttempts;
  private final RetryBackoffSpec baseRetrySpec;

  public CoinGeckoMarketDataProvider(
      WebClient.Builder builder,
      MarketDataProperties properties,
      MarketDataQuotaTracker quotaTracker,
      CryptoSymbolMapper mapper,
      Environment environment) {
    this.properties = Objects.requireNonNull(properties);
    this.quotaTracker = Objects.requireNonNull(quotaTracker);
    this.mapper = Objects.requireNonNull(mapper);

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

    this.coinGeckoClient =
        builder
            .baseUrl(normalizeUrl(properties.getCoinGeckoBaseUrl()))
            .clientConnector(connector)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();

    this.rssClient =
        WebClient.builder()
            .baseUrl(normalizeUrl(properties.getYahooRssBaseUrl()))
            .clientConnector(connector)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
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
  public Quote getQuote(String coinId) {
    requireSymbol(coinId);
    quotaTracker.increment();
    JsonNode root =
        withRetry(
                coinGeckoClient
                    .get()
                    .uri(
                        u ->
                            u.path("/api/v3/simple/price")
                                .queryParam("ids", coinId)
                                .queryParam("vs_currencies", "usd")
                                .queryParam("include_24hr_change", "true")
                                .queryParam("include_last_updated_at", "true")
                                .build())
                    .retrieve()
                    .onStatus(
                        s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(
                                    b ->
                                        new MarketDataRateLimitException(
                                            "CoinGecko rate limit reached")))
                    .onStatus(
                        HttpStatusCode::isError,
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(
                                    b ->
                                        new MarketDataClientException(
                                            "CoinGecko error " + r.statusCode() + ": " + b)))
                    .bodyToMono(JsonNode.class)
                    .onErrorMap(
                        WebClientResponseException.class,
                        ex ->
                            new MarketDataClientException(
                                "CoinGecko call failed: " + ex.getStatusCode(), ex))
                    .onErrorMap(
                        WebClientRequestException.class,
                        ex ->
                            new MarketDataClientException(
                                "CoinGecko request failed: " + ex.getMessage(), ex)),
                "quote",
                coinId)
            .block(properties.getReadTimeout());
    return toQuote(coinId, root);
  }

  @Override
  public List<DailyBar> getDailyHistory(String coinId) {
    requireSymbol(coinId);
    quotaTracker.increment();
    JsonNode root =
        withRetry(
                coinGeckoClient
                    .get()
                    .uri(
                        u ->
                            u.path("/api/v3/coins/{id}/ohlc")
                                .queryParam("vs_currency", "usd")
                                .queryParam("days", "90")
                                .build(coinId))
                    .retrieve()
                    .onStatus(
                        s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(
                                    b ->
                                        new MarketDataRateLimitException(
                                            "CoinGecko rate limit reached")))
                    .onStatus(
                        HttpStatusCode::isError,
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(
                                    b ->
                                        new MarketDataClientException(
                                            "CoinGecko error " + r.statusCode() + ": " + b)))
                    .bodyToMono(JsonNode.class)
                    .onErrorMap(
                        WebClientResponseException.class,
                        ex ->
                            new MarketDataClientException(
                                "CoinGecko call failed: " + ex.getStatusCode(), ex))
                    .onErrorMap(
                        WebClientRequestException.class,
                        ex ->
                            new MarketDataClientException(
                                "CoinGecko request failed: " + ex.getMessage(), ex)),
                "history",
                coinId)
            .block(properties.getReadTimeout());
    return toHistory(coinId, root);
  }

  @Override
  public CompanyOverview getOverview(String coinId) {
    requireSymbol(coinId);
    quotaTracker.increment();
    JsonNode root =
        withRetry(
                coinGeckoClient
                    .get()
                    .uri(
                        u ->
                            u.path("/api/v3/coins/{id}")
                                .queryParam("localization", "false")
                                .queryParam("tickers", "false")
                                .queryParam("market_data", "true")
                                .queryParam("community_data", "false")
                                .queryParam("developer_data", "false")
                                .build(coinId))
                    .retrieve()
                    .onStatus(
                        s -> s.value() == HttpStatus.NOT_FOUND.value(),
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(
                                    b ->
                                        new QuoteNotFoundException(
                                            "Overview not found for " + coinId)))
                    .onStatus(
                        s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(
                                    b ->
                                        new MarketDataRateLimitException(
                                            "CoinGecko rate limit reached")))
                    .onStatus(
                        HttpStatusCode::isError,
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(
                                    b ->
                                        new MarketDataClientException(
                                            "CoinGecko error " + r.statusCode() + ": " + b)))
                    .bodyToMono(JsonNode.class)
                    .onErrorMap(
                        WebClientResponseException.class,
                        ex ->
                            new MarketDataClientException(
                                "CoinGecko call failed: " + ex.getStatusCode(), ex))
                    .onErrorMap(
                        WebClientRequestException.class,
                        ex ->
                            new MarketDataClientException(
                                "CoinGecko request failed: " + ex.getMessage(), ex)),
                "overview",
                coinId)
            .block(properties.getReadTimeout());
    return toOverview(coinId, root);
  }

  @Override
  public List<NewsArticle> getNews(String tickerSymbol) {
    // tickerSymbol is the raw ticker (e.g. "BTC"), NOT a CoinGecko ID.
    // RoutingMarketDataProvider passes the original symbol here; mapper converts to "BTC-USD"
    // below.
    requireSymbol(tickerSymbol);
    quotaTracker.increment();
    String rssSymbol = mapper.toYahooRssSymbol(tickerSymbol);
    String xml =
        rssClient
            .get()
            .uri(u -> u.path("/rss/headline").queryParam("s", rssSymbol).build())
            .retrieve()
            .onStatus(HttpStatusCode::isError, r -> Mono.empty())
            .bodyToMono(String.class)
            .onErrorReturn("")
            .block(properties.getReadTimeout());
    return parseRss(xml);
  }

  // ── Parsing ─────────────────────────────────────────────────────────────────

  private Quote toQuote(String coinId, JsonNode root) {
    if (root == null || !root.has(coinId)) {
      throw new QuoteNotFoundException("Quote not found for " + coinId);
    }
    JsonNode data = root.path(coinId);
    if (data.isEmpty()) throw new QuoteNotFoundException("Quote not found for " + coinId);
    JsonNode usdNode = data.path("usd");
    if (!usdNode.isNumber()) throw new QuoteNotFoundException("Quote not found for " + coinId);
    double price = usdNode.asDouble();
    BigDecimal changePercent =
        data.path("usd_24h_change").isNumber()
            ? BigDecimal.valueOf(data.path("usd_24h_change").asDouble())
            : null;
    long epochSeconds = data.path("usd_last_updated_at").asLong(0);
    Instant timestamp = epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : Instant.now();
    return new Quote(coinId, BigDecimal.valueOf(price), changePercent, timestamp);
  }

  private List<DailyBar> toHistory(String coinId, JsonNode root) {
    if (root == null || !root.isArray() || root.isEmpty()) {
      throw new QuoteNotFoundException("History not found for " + coinId);
    }
    List<DailyBar> bars = new ArrayList<>();
    for (JsonNode row : root) {
      try {
        // [timestamp_ms, open, high, low, close]
        long timestampMs = row.get(0).asLong();
        LocalDate date = Instant.ofEpochMilli(timestampMs).atZone(ZoneOffset.UTC).toLocalDate();
        BigDecimal open = BigDecimal.valueOf(row.get(1).asDouble());
        BigDecimal high = BigDecimal.valueOf(row.get(2).asDouble());
        BigDecimal low = BigDecimal.valueOf(row.get(3).asDouble());
        BigDecimal close = BigDecimal.valueOf(row.get(4).asDouble());
        bars.add(new DailyBar(date, open, high, low, close, 0L));
      } catch (Exception ex) {
        log.warn("Skipping malformed OHLC row", ex);
      }
    }
    bars.sort(Comparator.comparing(DailyBar::date));
    return bars;
  }

  private CompanyOverview toOverview(String coinId, JsonNode root) {
    if (root == null || root.isEmpty()) {
      throw new QuoteNotFoundException("Overview not found for " + coinId);
    }
    String name = root.path("name").asText(null);
    JsonNode marketData = root.path("market_data");
    BigDecimal marketCap =
        marketData.path("market_cap").has("usd")
            ? BigDecimal.valueOf(marketData.path("market_cap").path("usd").asDouble())
            : null;
    // CoinGecko free tier has no 52-week high/low; map all-time high/low as the best approximation.
    BigDecimal ath =
        marketData.path("ath").has("usd")
            ? BigDecimal.valueOf(marketData.path("ath").path("usd").asDouble())
            : null;
    BigDecimal atl =
        marketData.path("atl").has("usd")
            ? BigDecimal.valueOf(marketData.path("atl").path("usd").asDouble())
            : null;
    return new CompanyOverview(
        coinId, name, "Cryptocurrency", null, marketCap, null, null, null, null, ath, atl);
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
          long id = Math.abs((long) Objects.hashCode(e.getUri()));
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

  private <T> Mono<T> withRetry(Mono<T> mono, String action, String coinId) {
    if (!retryEnabled || baseRetrySpec == null) return mono;
    return mono.retryWhen(
        baseRetrySpec.doBeforeRetry(
            s ->
                log.warn(
                    "Retrying CoinGecko {} for {} after attempt {}: {}",
                    action,
                    coinId,
                    s.totalRetries(),
                    s.failure().getMessage())));
  }

  private RuntimeException propagateFinalFailure(Throwable failure) {
    if (failure instanceof RuntimeException r) return r;
    return new MarketDataClientException("CoinGecko retries exhausted", failure);
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
