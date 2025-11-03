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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Profile;
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

@Component
@Profile("!dev")
public class RealMarketDataProvider implements MarketDataProvider {
  private static final DateTimeFormatter TRADING_DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

  private final WebClient webClient;
  private final MarketDataProperties properties;

  public RealMarketDataProvider(WebClient.Builder builder, MarketDataProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");

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
  }

  @Override
  public Quote getQuote(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }

    JsonNode response = retrieve(symbol).block(properties.getReadTimeout());
    return toQuote(symbol, response);
  }

  private Mono<JsonNode> retrieve(String symbol) {
    return webClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/query")
                    .queryParam("function", "GLOBAL_QUOTE")
                    .queryParam("symbol", symbol)
                    .queryParam("apikey", properties.getApiKey())
                    .build())
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
                                    ? "AlphaVantage rate limit reached"
                                    : "AlphaVantage rate limit reached: %s".formatted(body))))
        .onStatus(
            HttpStatusCode::isError,
            clientResponse ->
                clientResponse
                    .bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(
                        body ->
                            new MarketDataClientException(
                                "AlphaVantage error %s: %s"
                                    .formatted(clientResponse.statusCode(), body))))
        .bodyToMono(JsonNode.class)
        .onErrorMap(
            WebClientResponseException.class,
            ex ->
                new MarketDataClientException(
                    "AlphaVantage call failed with status %s".formatted(ex.getStatusCode()), ex))
        .onErrorMap(
            WebClientRequestException.class,
            ex ->
                new MarketDataClientException(
                    "AlphaVantage request failed: %s".formatted(ex.getMessage()), ex));
  }

  private Quote toQuote(String symbol, JsonNode root) {
    if (root == null || root.isEmpty()) {
      throw new MarketDataClientException("AlphaVantage response was empty");
    }

    checkForRateLimit(root);

    JsonNode globalQuote = root.path("Global Quote");
    if (globalQuote.isMissingNode() || globalQuote.isEmpty()) {
      throw new MarketDataClientException("AlphaVantage response missing 'Global Quote'");
    }

    String priceValue = textValue(globalQuote, "05. price");
    if (priceValue == null || priceValue.isBlank()) {
      throw new MarketDataClientException("AlphaVantage response missing price");
    }

    BigDecimal price;
    try {
      price = new BigDecimal(priceValue.trim());
    } catch (NumberFormatException ex) {
      throw new MarketDataClientException("AlphaVantage price was not numeric", ex);
    }

    return new Quote(symbol, price, extractTimestamp(globalQuote));
  }

  private void checkForRateLimit(JsonNode root) {
    String note = textValue(root, "Note");
    if (note != null && !note.isBlank()) {
      throw new MarketDataRateLimitException(note.trim());
    }

    String information = textValue(root, "Information");
    if (information != null && !information.isBlank()) {
      throw new MarketDataRateLimitException(information.trim());
    }
  }

  private static String textValue(JsonNode node, String fieldName) {
    JsonNode raw = node.get(fieldName);
    return raw != null ? raw.asText(null) : null;
  }

  private Instant extractTimestamp(JsonNode globalQuote) {
    String tradingDay = textValue(globalQuote, "07. latest trading day");
    if (tradingDay != null && !tradingDay.isBlank()) {
      try {
        return LocalDate.parse(tradingDay, TRADING_DAY_FORMAT)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC);
      } catch (DateTimeParseException ex) {
        throw new MarketDataClientException("AlphaVantage trading day malformed", ex);
      }
    }

    return Instant.now();
  }

  private static String normalizeBaseUrl(String baseUrl) {
    if (baseUrl == null) {
      throw new MarketDataClientException("Base URL must not be null");
    }
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
