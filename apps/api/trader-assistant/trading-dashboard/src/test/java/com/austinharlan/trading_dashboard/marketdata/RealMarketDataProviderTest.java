package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

class RealMarketDataProviderTest {
  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private MockWebServer server;
  private MarketDataProperties properties;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    properties = new MarketDataProperties();
    properties.setBaseUrl(server.url("/").toString());
    properties.setApiKey("test-key");
    properties.setHealthSymbol("AAPL");
    properties.setConnectTimeout(TIMEOUT);
    properties.setReadTimeout(TIMEOUT);
    properties.setWriteTimeout(TIMEOUT);
    properties.getRetry().setInitialBackoff(Duration.ofMillis(10));
    properties.getRetry().setMaxBackoff(Duration.ofMillis(25));
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void shouldReturnQuoteWhenApiResponds() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "Global Quote": {
                    "01. symbol": "AAPL",
                    "05. price": "123.45",
                    "07. latest trading day": "2024-09-15"
                  }
                }
                """));

    RealMarketDataProvider provider = provider();

    Quote quote = provider.getQuote("AAPL");

    assertThat(quote.symbol()).isEqualTo("AAPL");
    assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("123.45"));
    assertThat(quote.timestamp()).isEqualTo(Instant.parse("2024-09-15T00:00:00Z"));
  }

  @Test
  void shouldThrowWhenApiReturnsServerError() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(500)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"error\":\"internal\"}"));

    RealMarketDataProvider provider = provider();

    assertThatThrownBy(() -> provider.getQuote("AAPL"))
        .isInstanceOf(MarketDataClientException.class)
        .hasMessageContaining("AlphaVantage error");
  }

  @Test
  void shouldThrowWhenResponseMissingGlobalQuote() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"message\":\"Invalid\"}"));

    RealMarketDataProvider provider = provider();

    assertThatThrownBy(() -> provider.getQuote("AAPL"))
        .isInstanceOf(QuoteNotFoundException.class)
        .hasMessageContaining("Quote was not found");
  }

  @Test
  void shouldSurfaceRateLimitOn429Response() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(429)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"message\":\"Too many requests\"}"));

    RealMarketDataProvider provider = provider();

    assertThatThrownBy(() -> provider.getQuote("AAPL"))
        .isInstanceOf(MarketDataRateLimitException.class)
        .hasMessageContaining("rate limit");
  }

  @Test
  void shouldSurfaceRateLimitWhenNoteInPayload() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "Note": "Thank you for using Alpha Vantage! ... API call frequency is 5 per minute.",
                  "Global Quote": {}
                }
                """));

    RealMarketDataProvider provider = provider();

    assertThatThrownBy(() -> provider.getQuote("AAPL"))
        .isInstanceOf(MarketDataRateLimitException.class)
        .hasMessageContaining("API call frequency");
  }

  @Test
  void shouldRetryInProdAndSucceedWhenTransientError() {
    properties.getRetry().setMaxAttempts(3);

    server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "Global Quote": {
                    "01. symbol": "AAPL",
                    "05. price": "123.45",
                    "07. latest trading day": "2024-09-15"
                  }
                }
                """));

    RealMarketDataProvider provider = provider("prod");

    Quote quote = provider.getQuote("AAPL");

    assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("123.45"));
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void shouldNotRetryRateLimitResponsesInProd() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(429)
            .addHeader("Content-Type", "application/json")
            .setBody("{}"));

    RealMarketDataProvider provider = provider("prod");

    assertThatThrownBy(() -> provider.getQuote("AAPL"))
        .isInstanceOf(MarketDataRateLimitException.class);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void shouldFailAfterConfiguredAttemptsInProd() {
    properties.getRetry().setMaxAttempts(3);

    server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));
    server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));
    server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));

    RealMarketDataProvider provider = provider("prod");

    assertThatThrownBy(() -> provider.getQuote("AAPL"))
        .isInstanceOf(MarketDataClientException.class)
        .hasMessageContaining("AlphaVantage error");
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  private RealMarketDataProvider provider(String... activeProfiles) {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(activeProfiles);
    return new RealMarketDataProvider(WebClient.builder(), properties, environment);
  }
}
