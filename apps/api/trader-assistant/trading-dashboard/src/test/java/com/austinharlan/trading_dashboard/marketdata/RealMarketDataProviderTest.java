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
                  "c": 123.45,
                  "d": 1.23,
                  "dp": 1.01,
                  "h": 124.00,
                  "l": 122.50,
                  "o": 122.80,
                  "pc": 122.22,
                  "t": 1726358400
                }
                """));

    RealMarketDataProvider provider = provider();

    Quote quote = provider.getQuote("AAPL");

    assertThat(quote.symbol()).isEqualTo("AAPL");
    assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("123.45"));
    assertThat(quote.timestamp()).isEqualTo(Instant.ofEpochSecond(1726358400L));
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
        .hasMessageContaining("Finnhub error");
  }

  @Test
  void shouldThrowWhenQuotePriceIsZero() {
    // Finnhub returns c=0 for unknown or unresolvable symbols
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "c": 0,
                  "d": 0,
                  "dp": 0,
                  "h": 0,
                  "l": 0,
                  "o": 0,
                  "pc": 0,
                  "t": 0
                }
                """));

    RealMarketDataProvider provider = provider();

    assertThatThrownBy(() -> provider.getQuote("INVALID"))
        .isInstanceOf(QuoteNotFoundException.class)
        .hasMessageContaining("Quote was not found");
  }

  @Test
  void shouldSurfaceRateLimitOn429Response() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(429)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"error\":\"API limit reached.\"}"));

    RealMarketDataProvider provider = provider();

    assertThatThrownBy(() -> provider.getQuote("AAPL"))
        .isInstanceOf(MarketDataRateLimitException.class)
        .hasMessageContaining("rate limit");
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
                  "c": 123.45,
                  "d": 1.23,
                  "dp": 1.01,
                  "h": 124.00,
                  "l": 122.50,
                  "o": 122.80,
                  "pc": 122.22,
                  "t": 1726358400
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
        .hasMessageContaining("Finnhub error");
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void shouldReturnHistoryWhenCandleApiResponds() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "c": [150.10, 152.50],
                  "h": [151.00, 153.00],
                  "l": [149.00, 151.50],
                  "o": [149.50, 151.00],
                  "s": "ok",
                  "t": [1690848000, 1690934400],
                  "v": [34000000, 28000000]
                }
                """));

    RealMarketDataProvider provider = provider();

    java.util.List<DailyBar> bars = provider.getDailyHistory("AAPL");

    assertThat(bars).hasSize(2);
    assertThat(bars.get(0).close()).isEqualByComparingTo(new BigDecimal("150.1"));
    assertThat(bars.get(1).close()).isEqualByComparingTo(new BigDecimal("152.5"));
    assertThat(bars.get(0).date()).isBefore(bars.get(1).date());
  }

  @Test
  void shouldThrowWhenCandleStatusIsNoData() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"s\":\"no_data\"}"));

    RealMarketDataProvider provider = provider();

    assertThatThrownBy(() -> provider.getDailyHistory("INVALID"))
        .isInstanceOf(QuoteNotFoundException.class)
        .hasMessageContaining("History was not found");
  }

  @Test
  void shouldReturnOverviewFromProfileAndMetrics() {
    // profile2 response
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "name": "Apple Inc",
                  "finnhubIndustry": "Technology",
                  "marketCapitalization": 3032893.0,
                  "ticker": "AAPL",
                  "country": "US"
                }
                """));
    // metric response
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "metric": {
                    "52WeekHigh": 199.62,
                    "52WeekLow": 124.17,
                    "beta": 1.2,
                    "dividendYieldIndicatedAnnual": 0.0055,
                    "epsInclExtraItemsTTM": 6.26,
                    "peTTM": 29.48
                  }
                }
                """));

    RealMarketDataProvider provider = provider();

    CompanyOverview overview = provider.getOverview("AAPL");

    assertThat(overview.symbol()).isEqualTo("AAPL");
    assertThat(overview.name()).isEqualTo("Apple Inc");
    assertThat(overview.sector()).isEqualTo("Technology");
    // marketCap is marketCapitalization (millions) * 1_000_000
    assertThat(overview.marketCap())
        .isEqualByComparingTo(new BigDecimal("3032893000000.0"));
    assertThat(overview.pe()).isEqualByComparingTo(new BigDecimal("29.48"));
    assertThat(overview.beta()).isEqualByComparingTo(new BigDecimal("1.2"));
  }

  private RealMarketDataProvider provider(String... activeProfiles) {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(activeProfiles);
    return new RealMarketDataProvider(
        WebClient.builder(), properties, new MarketDataQuotaTracker(), environment);
  }
}
