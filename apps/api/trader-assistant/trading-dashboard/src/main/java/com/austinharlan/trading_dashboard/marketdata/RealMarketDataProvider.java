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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

@Component
@Profile("!dev")
public class RealMarketDataProvider implements MarketDataProvider {
  private static final Logger log = LoggerFactory.getLogger(RealMarketDataProvider.class);
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

  private final WebClient webClient;
  private final WebClient rssClient;
  private final MarketDataProperties properties;
  private final YahooCrumbProvider crumbProvider;

  public RealMarketDataProvider(
      WebClient.Builder builder,
      MarketDataProperties properties,
      YahooCrumbProvider crumbProvider) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.crumbProvider = Objects.requireNonNull(crumbProvider, "crumbProvider must not be null");

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

    String baseUrl = normalizeUrl(properties.getQuery2BaseUrl());

    this.webClient =
        builder
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .build();

    this.rssClient =
        WebClient.builder()
            .baseUrl(normalizeUrl(properties.getYahooRssBaseUrl()))
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .build();
  }

  @Override
  public Quote getQuote(String symbol) {
    requireSymbol(symbol);
    JsonNode result = fetchQuoteSummary(symbol, "price");
    return toQuote(symbol, result);
  }

  @Override
  public CompanyOverview getOverview(String symbol) {
    requireSymbol(symbol);
    JsonNode result =
        fetchQuoteSummary(symbol, "price,defaultKeyStatistics,summaryDetail,assetProfile");
    return toOverview(symbol, result);
  }

  @Override
  public List<DailyBar> getDailyHistory(String symbol) {
    requireSymbol(symbol);
    JsonNode response =
        webClient
            .get()
            .uri("/v8/finance/chart/{symbol}?range=100d&interval=1d", symbol)
            .retrieve()
            .onStatus(
                status -> status.value() == 404,
                r ->
                    r.bodyToMono(String.class)
                        .map(
                            body ->
                                new QuoteNotFoundException(
                                    "History was not found for %s".formatted(symbol))))
            .onStatus(
                HttpStatusCode::isError,
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(
                            body ->
                                new MarketDataClientException(
                                    "Yahoo chart error %s: %s".formatted(r.statusCode(), body))))
            .bodyToMono(JsonNode.class)
            .doOnError(
                ex -> log.warn("Yahoo chart request for {} failed: {}", symbol, ex.getMessage()))
            .onErrorMap(
                WebClientResponseException.class,
                ex ->
                    new MarketDataClientException(
                        "Yahoo chart call failed with status %s".formatted(ex.getStatusCode()), ex))
            .onErrorMap(
                WebClientRequestException.class,
                ex ->
                    new MarketDataClientException(
                        "Yahoo chart request failed: %s".formatted(ex.getMessage()), ex))
            .block(properties.getReadTimeout());
    return toHistory(symbol, response);
  }

  @Override
  public List<NewsArticle> getNews(String symbol) {
    requireSymbol(symbol);
    try {
      String rssXml =
          rssClient
              .get()
              .uri("/rss/2.0/headline?s={symbol}&region=US&lang=en-US", symbol)
              .accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML)
              .retrieve()
              .bodyToMono(String.class)
              .block(properties.getReadTimeout());

      if (rssXml == null || rssXml.isBlank()) {
        return List.of();
      }

      SyndFeedInput input = new SyndFeedInput();
      SyndFeed feed = input.build(new StringReader(rssXml));
      return toNews(feed.getEntries());
    } catch (Exception ex) {
      log.warn("Failed to fetch Yahoo news for {}: {}", symbol, ex.getMessage());
      return List.of();
    }
  }

  // ── quoteSummary with crumb ──────────────────────────────────────────────

  private JsonNode fetchQuoteSummary(String symbol, String modules) {
    try {
      return doFetchQuoteSummary(symbol, modules);
    } catch (MarketDataClientException ex) {
      if (ex.getMessage() != null
          && (ex.getMessage().contains("401") || ex.getMessage().contains("403"))) {
        log.info("Crumb rejected, refreshing and retrying for {}", symbol);
        crumbProvider.invalidate();
        return doFetchQuoteSummary(symbol, modules);
      }
      throw ex;
    }
  }

  private JsonNode doFetchQuoteSummary(String symbol, String modules) {
    JsonNode root =
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v10/finance/quoteSummary/{symbol}")
                        .queryParam("modules", modules)
                        .queryParam("crumb", crumbProvider.getCrumb())
                        .build(symbol))
            .header("Cookie", crumbProvider.getCookie())
            .retrieve()
            .onStatus(
                status -> status.value() == 404,
                r ->
                    r.bodyToMono(String.class)
                        .map(
                            body ->
                                new QuoteNotFoundException(
                                    "Quote was not found for %s".formatted(symbol))))
            .onStatus(
                HttpStatusCode::isError,
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(
                            body ->
                                new MarketDataClientException(
                                    "Yahoo Finance error %s: %s".formatted(r.statusCode(), body))))
            .bodyToMono(JsonNode.class)
            .doOnSubscribe(sub -> log.debug("Requesting Yahoo quoteSummary for {}", symbol))
            .doOnError(
                ex -> log.warn("Yahoo quoteSummary for {} failed: {}", symbol, ex.getMessage(), ex))
            .onErrorMap(
                WebClientResponseException.class,
                ex ->
                    new MarketDataClientException(
                        "Yahoo call failed with status %s".formatted(ex.getStatusCode()), ex))
            .onErrorMap(
                WebClientRequestException.class,
                ex ->
                    new MarketDataClientException(
                        "Yahoo request failed: %s".formatted(ex.getMessage()), ex))
            .block(properties.getReadTimeout());

    if (root == null) {
      throw new QuoteNotFoundException("Quote was not found for %s".formatted(symbol));
    }

    JsonNode result = root.path("quoteSummary").path("result");
    if (!result.isArray() || result.isEmpty()) {
      throw new QuoteNotFoundException("Quote was not found for %s".formatted(symbol));
    }
    return result.get(0);
  }

  // ── Quote parsing ────────────────────────────────────────────────────────

  private Quote toQuote(String symbol, JsonNode result) {
    JsonNode price = result.path("price");
    BigDecimal marketPrice = rawBigDecimal(price, "regularMarketPrice");
    if (marketPrice == null || marketPrice.signum() == 0) {
      throw new QuoteNotFoundException("Quote was not found for %s".formatted(symbol));
    }

    BigDecimal changePercent = rawBigDecimal(price, "regularMarketChangePercent");
    long epochSeconds = price.path("regularMarketTime").asLong(0);
    Instant timestamp = epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : Instant.now();

    return new Quote(symbol, marketPrice, changePercent, timestamp);
  }

  // ── Overview parsing ─────────────────────────────────────────────────────

  private CompanyOverview toOverview(String symbol, JsonNode result) {
    JsonNode price = result.path("price");
    JsonNode stats = result.path("defaultKeyStatistics");
    JsonNode summary = result.path("summaryDetail");
    JsonNode profile = result.path("assetProfile");

    String name = safeText(price, "shortName");
    String sector = safeText(profile, "sector");
    String industry = safeText(profile, "industry");
    BigDecimal marketCap = rawBigDecimal(price, "marketCap");
    BigDecimal pe = rawBigDecimal(summary, "trailingPE");
    BigDecimal eps = rawBigDecimal(stats, "trailingEps");
    BigDecimal dividendYield = rawBigDecimal(summary, "dividendYield");
    BigDecimal beta = rawBigDecimal(stats, "beta");
    BigDecimal high52 = rawBigDecimal(summary, "fiftyTwoWeekHigh");
    BigDecimal low52 = rawBigDecimal(summary, "fiftyTwoWeekLow");

    if (name == null && marketCap == null) {
      throw new QuoteNotFoundException("Overview was not found for %s".formatted(symbol));
    }

    return new CompanyOverview(
        symbol, name, sector, industry, marketCap, pe, eps, dividendYield, beta, high52, low52);
  }

  // ── History parsing ──────────────────────────────────────────────────────

  private List<DailyBar> toHistory(String symbol, JsonNode root) {
    if (root == null) {
      throw new QuoteNotFoundException("History was not found for %s".formatted(symbol));
    }

    JsonNode results = root.path("chart").path("result");
    if (!results.isArray() || results.isEmpty()) {
      throw new QuoteNotFoundException("History was not found for %s".formatted(symbol));
    }

    JsonNode first = results.get(0);
    JsonNode timestamps = first.path("timestamp");
    JsonNode indicators = first.path("indicators").path("quote");
    if (!timestamps.isArray() || timestamps.isEmpty() || !indicators.isArray()) {
      throw new QuoteNotFoundException("History was not found for %s".formatted(symbol));
    }

    JsonNode quote = indicators.get(0);
    JsonNode opens = quote.path("open");
    JsonNode highs = quote.path("high");
    JsonNode lows = quote.path("low");
    JsonNode closes = quote.path("close");
    JsonNode volumes = quote.path("volume");

    List<DailyBar> bars = new ArrayList<>();
    for (int i = 0; i < timestamps.size(); i++) {
      try {
        if (closes.get(i) == null || closes.get(i).isNull()) {
          continue;
        }
        LocalDate date =
            Instant.ofEpochSecond(timestamps.get(i).asLong()).atZone(ZoneOffset.UTC).toLocalDate();
        bars.add(
            new DailyBar(
                date,
                BigDecimal.valueOf(opens.get(i).asDouble()),
                BigDecimal.valueOf(highs.get(i).asDouble()),
                BigDecimal.valueOf(lows.get(i).asDouble()),
                BigDecimal.valueOf(closes.get(i).asDouble()),
                volumes.get(i).asLong(0)));
      } catch (Exception ex) {
        log.warn("Skipping malformed chart entry at index {}", i, ex);
      }
    }

    bars.sort(Comparator.comparing(DailyBar::date));
    return bars;
  }

  // ── News parsing (RSS) ───────────────────────────────────────────────────

  private List<NewsArticle> toNews(List<SyndEntry> entries) {
    if (entries == null || entries.isEmpty()) {
      return List.of();
    }
    List<NewsArticle> articles = new ArrayList<>();
    for (SyndEntry entry : entries) {
      try {
        String link = entry.getLink();
        String guid = entry.getUri();
        long id = (guid != null ? guid : link != null ? link : "").hashCode();
        String headline = entry.getTitle();
        String summary = entry.getDescription() != null ? entry.getDescription().getValue() : null;
        Instant publishedAt =
            entry.getPublishedDate() != null ? entry.getPublishedDate().toInstant() : Instant.now();
        if (headline != null && link != null) {
          articles.add(
              new NewsArticle(id, headline, summary, "Yahoo Finance", link, null, publishedAt));
        }
      } catch (Exception ex) {
        log.warn("Skipping malformed RSS entry", ex);
      }
    }
    return articles.stream()
        .sorted(Comparator.comparing(NewsArticle::publishedAt).reversed())
        .limit(10)
        .toList();
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private void requireSymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
  }

  private static BigDecimal rawBigDecimal(JsonNode parent, String field) {
    JsonNode node = parent.path(field);
    if (node.isMissingNode() || node.isNull()) {
      return null;
    }
    JsonNode raw = node.path("raw");
    if (!raw.isMissingNode() && !raw.isNull() && raw.isNumber()) {
      return BigDecimal.valueOf(raw.asDouble());
    }
    if (node.isNumber()) {
      return BigDecimal.valueOf(node.asDouble());
    }
    return null;
  }

  private static String safeText(JsonNode parent, String field) {
    JsonNode node = parent.path(field);
    if (node.isMissingNode() || node.isNull()) {
      return null;
    }
    String text = node.asText(null);
    return (text != null && !text.isBlank()) ? text.trim() : null;
  }

  private static String normalizeUrl(String url) {
    if (url == null) {
      throw new MarketDataClientException("URL must not be null");
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
