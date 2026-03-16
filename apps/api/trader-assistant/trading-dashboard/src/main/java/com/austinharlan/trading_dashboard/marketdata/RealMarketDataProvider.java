package com.austinharlan.trading_dashboard.marketdata;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

@Component
@Profile("!dev")
public class RealMarketDataProvider implements MarketDataProvider {
  private static final Set<String> NULL_MARKERS =
      Set.of("none", "-", "null", "");

  private static final Logger log = LoggerFactory.getLogger(RealMarketDataProvider.class);

  private final WebClient webClient;
  private final MarketDataProperties properties;
  private final MarketDataQuotaTracker quotaTracker;
  private final boolean retryEnabled;
  private final int maxAttempts;
  private final RetryBackoffSpec baseRetrySpec;

  public RealMarketDataProvider(
      WebClient.Builder builder,
      MarketDataProperties properties,
      MarketDataQuotaTracker quotaTracker,
      Environment environment) {
    this.quotaTracker = Objects.requireNonNull(quotaTracker, "quotaTracker must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    Objects.requireNonNull(environment, "environment must not be null");

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

    String baseUrl = normalizeBaseUrl(properties.getBaseUrl());

    this.webClient =
        builder
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();

    this.retryEnabled =
        Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> profile.equalsIgnoreCase("prod"));
    this.maxAttempts = Math.max(1, properties.getRetry().getMaxAttempts());

    this.baseRetrySpec =
        maxAttempts > 1
            ? Retry.backoff(maxAttempts - 1, properties.getRetry().getInitialBackoff())
                .maxBackoff(properties.getRetry().getMaxBackoff())
                .filter(
                    throwable ->
                        throwable instanceof MarketDataClientException
                            && !(throwable instanceof MarketDataRateLimitException))
                .onRetryExhaustedThrow((spec, signal) -> propagateFinalFailure(signal.failure()))
            : null;
  }

  @Override
  public Quote getQuote(String symbol) {
    requireSymbol(symbol);
    quotaTracker.increment();
    JsonNode response =
        retrieveEndpoint("/quote", symbol, Map.of()).block(properties.getReadTimeout());
    return toQuote(symbol, response);
  }

  @Override
  public CompanyOverview getOverview(String symbol) {
    requireSymbol(symbol);
    // Two concurrent requests — increment quota for each
    quotaTracker.increment();
    quotaTracker.increment();
    Mono<JsonNode> profileMono = retrieveEndpoint("/stock/profile2", symbol, Map.of());
    Mono<JsonNode> metricMono =
        retrieveEndpoint("/stock/metric", symbol, Map.of("metric", "all"));
    return Mono.zip(profileMono, metricMono)
        .map(tuple -> toOverview(symbol, tuple.getT1(), tuple.getT2()))
        .block(properties.getReadTimeout());
  }

  @Override
  public List<DailyBar> getDailyHistory(String symbol) {
    requireSymbol(symbol);
    quotaTracker.increment();
    long to = Instant.now().getEpochSecond();
    long from = Instant.now().minus(100, ChronoUnit.DAYS).getEpochSecond();
    JsonNode response =
        retrieveEndpoint(
                "/stock/candle",
                symbol,
                Map.of("resolution", "D", "from", String.valueOf(from), "to", String.valueOf(to)))
            .block(properties.getReadTimeout());
    return toHistory(symbol, response);
  }

  public MarketDataQuotaTracker getQuotaTracker() {
    return quotaTracker;
  }

  private Mono<JsonNode> retrieveEndpoint(
      String path, String symbol, Map<String, String> extraParams) {
    Mono<JsonNode> request =
        webClient
            .get()
            .uri(
                uriBuilder -> {
                  uriBuilder
                      .path(path)
                      .queryParam("symbol", symbol)
                      .queryParam("token", properties.getApiKey());
                  extraParams.forEach(uriBuilder::queryParam);
                  return uriBuilder.build();
                })
            .retrieve()
            .onStatus(
                status -> status.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                clientResponse ->
                    clientResponse
                        .bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(
                            body ->
                                new MarketDataRateLimitException(
                                    body.isBlank()
                                        ? "Finnhub rate limit reached"
                                        : "Finnhub rate limit reached: %s".formatted(body))))
            .onStatus(
                HttpStatusCode::isError,
                clientResponse ->
                    clientResponse
                        .bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(
                            body ->
                                new MarketDataClientException(
                                    "Finnhub error %s: %s"
                                        .formatted(clientResponse.statusCode(), body))))
            .bodyToMono(JsonNode.class)
            .doOnSubscribe(sub -> log.debug("Requesting Finnhub {} for {}", path, symbol))
            .doOnSuccess(
                body -> log.debug("Received Finnhub {} payload for {}", path, symbol))
            .doOnError(
                ex ->
                    log.warn(
                        "Finnhub {} request for {} failed: {}",
                        path,
                        symbol,
                        ex.getMessage(),
                        ex))
            .onErrorMap(
                WebClientResponseException.class,
                ex ->
                    new MarketDataClientException(
                        "Finnhub call failed with status %s".formatted(ex.getStatusCode()), ex))
            .onErrorMap(
                WebClientRequestException.class,
                ex ->
                    new MarketDataClientException(
                        "Finnhub request failed: %s".formatted(ex.getMessage()), ex));

    if (!retryEnabled || baseRetrySpec == null) {
      return request;
    }

    return request.retryWhen(
        baseRetrySpec.doBeforeRetry(
            retrySignal ->
                log.warn(
                    "Retrying Finnhub {} for {} after attempt {} failed (max {} attempts): {}",
                    path,
                    symbol,
                    retrySignal.totalRetries(),
                    maxAttempts,
                    retrySignal.failure().getMessage())));
  }

  private RuntimeException propagateFinalFailure(Throwable failure) {
    if (failure instanceof RuntimeException runtime) {
      return runtime;
    }
    return new MarketDataClientException("Finnhub retries exhausted", failure);
  }

  // ── Quote parsing ──────────────────────────────────────────────────────────

  private Quote toQuote(String symbol, JsonNode root) {
    if (root == null || root.isMissingNode()) {
      throw new QuoteNotFoundException("Quote was not found for %s".formatted(symbol));
    }

    JsonNode cNode = root.path("c");
    if (cNode.isMissingNode() || cNode.isNull()) {
      throw new QuoteNotFoundException("Quote was not found for %s".formatted(symbol));
    }

    double price = cNode.asDouble(0);
    if (price == 0) {
      throw new QuoteNotFoundException("Quote was not found for %s".formatted(symbol));
    }

    long epochSeconds = root.path("t").asLong(0);
    Instant timestamp = epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : Instant.now();

    return new Quote(symbol, BigDecimal.valueOf(price), timestamp);
  }

  // ── Overview parsing ───────────────────────────────────────────────────────

  private CompanyOverview toOverview(String symbol, JsonNode profile, JsonNode metricRoot) {
    if ((profile == null || profile.isEmpty()) && (metricRoot == null || metricRoot.isEmpty())) {
      throw new QuoteNotFoundException("Overview was not found for %s".formatted(symbol));
    }

    String name = safeText(profile, "name");
    // Finnhub provides a single industry classification via finnhubIndustry
    String sector = safeText(profile, "finnhubIndustry");

    // marketCapitalization from Finnhub is in millions USD
    BigDecimal marketCapMillions = safeBigDecimal(profile, "marketCapitalization");
    BigDecimal marketCap =
        marketCapMillions != null
            ? marketCapMillions.multiply(BigDecimal.valueOf(1_000_000))
            : null;

    JsonNode metric = (metricRoot != null && !metricRoot.isMissingNode())
        ? metricRoot.path("metric")
        : null;

    BigDecimal pe = metric != null ? safeBigDecimal(metric, "peTTM") : null;
    BigDecimal eps = metric != null ? safeBigDecimal(metric, "epsInclExtraItemsTTM") : null;
    BigDecimal dividendYield =
        metric != null ? safeBigDecimal(metric, "dividendYieldIndicatedAnnual") : null;
    BigDecimal beta = metric != null ? safeBigDecimal(metric, "beta") : null;
    BigDecimal high52 = metric != null ? safeBigDecimal(metric, "52WeekHigh") : null;
    BigDecimal low52 = metric != null ? safeBigDecimal(metric, "52WeekLow") : null;

    return new CompanyOverview(symbol, name, sector, null, marketCap, pe, eps, dividendYield,
        beta, high52, low52);
  }

  // ── History parsing ────────────────────────────────────────────────────────

  private List<DailyBar> toHistory(String symbol, JsonNode root) {
    if (root == null || root.isEmpty()) {
      throw new QuoteNotFoundException("History was not found for %s".formatted(symbol));
    }

    String status = root.path("s").asText("");
    if (!"ok".equals(status)) {
      throw new QuoteNotFoundException("History was not found for %s".formatted(symbol));
    }

    JsonNode closes = root.path("c");
    JsonNode highs = root.path("h");
    JsonNode lows = root.path("l");
    JsonNode opens = root.path("o");
    JsonNode timestamps = root.path("t");
    JsonNode volumes = root.path("v");

    if (!closes.isArray() || closes.isEmpty()) {
      throw new QuoteNotFoundException("History was not found for %s".formatted(symbol));
    }

    List<DailyBar> bars = new ArrayList<>();
    for (int i = 0; i < closes.size(); i++) {
      try {
        LocalDate date =
            Instant.ofEpochSecond(timestamps.get(i).asLong())
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        bars.add(
            new DailyBar(
                date,
                BigDecimal.valueOf(opens.get(i).asDouble()),
                BigDecimal.valueOf(highs.get(i).asDouble()),
                BigDecimal.valueOf(lows.get(i).asDouble()),
                BigDecimal.valueOf(closes.get(i).asDouble()),
                volumes.get(i).asLong(0)));
      } catch (Exception ex) {
        log.warn("Skipping malformed candle entry at index {}", i, ex);
      }
    }

    bars.sort(Comparator.comparing(DailyBar::date));
    return bars;
  }

  // ── Shared helpers ─────────────────────────────────────────────────────────

  private void requireSymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
  }

  private static String textValue(JsonNode node, String fieldName) {
    if (node == null || node.isMissingNode()) {
      return null;
    }
    JsonNode raw = node.get(fieldName);
    return raw != null ? raw.asText(null) : null;
  }

  private static String safeText(JsonNode node, String fieldName) {
    String val = textValue(node, fieldName);
    if (val == null || NULL_MARKERS.contains(val.trim().toLowerCase(java.util.Locale.ROOT))) {
      return null;
    }
    return val.trim();
  }

  private static BigDecimal safeBigDecimal(JsonNode node, String fieldName) {
    if (node == null || node.isMissingNode()) {
      return null;
    }
    JsonNode raw = node.get(fieldName);
    if (raw == null || raw.isNull() || raw.isMissingNode()) {
      return null;
    }
    if (raw.isNumber()) {
      return BigDecimal.valueOf(raw.asDouble());
    }
    String val = raw.asText(null);
    if (val == null || NULL_MARKERS.contains(val.trim().toLowerCase(java.util.Locale.ROOT))) {
      return null;
    }
    try {
      return new BigDecimal(val.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static String normalizeBaseUrl(String baseUrl) {
    if (baseUrl == null) {
      throw new MarketDataClientException("Base URL must not be null");
    }
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
