package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

class YahooFinanceMarketDataProviderTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private MockWebServer server;
  private MarketDataProperties properties;
  private YahooCrumbProvider mockCrumb;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    String url = server.url("/").toString();
    properties = new MarketDataProperties();
    properties.setBaseUrl(url);
    properties.setQuery2BaseUrl(url);
    properties.setYahooRssBaseUrl(url);
    properties.setCoinGeckoBaseUrl(url);
    properties.setConnectTimeout(TIMEOUT);
    properties.setReadTimeout(TIMEOUT);
    properties.setWriteTimeout(TIMEOUT);
    properties.getRetry().setInitialBackoff(Duration.ofMillis(10));
    properties.getRetry().setMaxBackoff(Duration.ofMillis(25));

    mockCrumb = mock(YahooCrumbProvider.class);
    when(mockCrumb.getCrumb()).thenReturn("test-crumb");
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  // ── getQuote ────────────────────────────────────────────────────────────────

  @Test
  void getQuoteParsesResponseCorrectly() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "quoteResponse": {
                    "result": [{
                      "symbol": "AAPL",
                      "regularMarketPrice": 178.72,
                      "regularMarketChangePercent": 1.23,
                      "regularMarketTime": 1709900400
                    }],
                    "error": null
                  }
                }
                """));

    Quote quote = provider().getQuote("AAPL");

    assertThat(quote.symbol()).isEqualTo("AAPL");
    assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("178.72"));
    assertThat(quote.changePercent()).isEqualByComparingTo(new BigDecimal("1.23"));
  }

  @Test
  void getQuoteThrowsOnEmptyResultArray() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {"quoteResponse": {"result": [], "error": null}}
                """));

    assertThatThrownBy(() -> provider().getQuote("INVALID"))
        .isInstanceOf(QuoteNotFoundException.class);
  }

  @Test
  void getQuoteThrowsRateLimitOn429() {
    server.enqueue(new MockResponse().setResponseCode(429).setBody("{}"));

    assertThatThrownBy(() -> provider().getQuote("AAPL"))
        .isInstanceOf(MarketDataRateLimitException.class);
  }

  // ── getDailyHistory ─────────────────────────────────────────────────────────

  @Test
  void getDailyHistoryParsesTwoBars() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "chart": {
                    "result": [{
                      "timestamp": [1690848000, 1690934400],
                      "indicators": {
                        "quote": [{
                          "open":  [149.50, 151.00],
                          "high":  [151.00, 153.00],
                          "low":   [149.00, 151.50],
                          "close": [150.10, 152.50],
                          "volume":[34000000, 28000000]
                        }]
                      }
                    }],
                    "error": null
                  }
                }
                """));

    List<DailyBar> bars = provider().getDailyHistory("AAPL");

    assertThat(bars).hasSize(2);
    assertThat(bars.get(0).close()).isEqualByComparingTo(new BigDecimal("150.10"));
    assertThat(bars.get(1).close()).isEqualByComparingTo(new BigDecimal("152.50"));
    assertThat(bars.get(0).date()).isBefore(bars.get(1).date());
  }

  @Test
  void getDailyHistoryThrowsOnMissingResult() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {"chart": {"result": null, "error": null}}
                """));

    assertThatThrownBy(() -> provider().getDailyHistory("INVALID"))
        .isInstanceOf(QuoteNotFoundException.class);
  }

  // ── getOverview ─────────────────────────────────────────────────────────────

  @Test
  void getOverviewParsesAllFields() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "quoteSummary": {
                    "result": [{
                      "price": {
                        "shortName": "Apple Inc.",
                        "marketCap": {"raw": 2800000000000.0}
                      },
                      "summaryProfile": {"sector": "Technology"},
                      "summaryDetail": {
                        "trailingPE":    {"raw": 28.5},
                        "dividendYield": {"raw": 0.006}
                      },
                      "defaultKeyStatistics": {
                        "beta":             {"raw": 1.23},
                        "trailingEps":      {"raw": 6.05},
                        "fiftyTwoWeekHigh": {"raw": 198.23},
                        "fiftyTwoWeekLow":  {"raw": 124.17}
                      }
                    }],
                    "error": null
                  }
                }
                """));

    CompanyOverview overview = provider().getOverview("AAPL");

    assertThat(overview.name()).isEqualTo("Apple Inc.");
    assertThat(overview.sector()).isEqualTo("Technology");
    assertThat(overview.marketCap()).isEqualByComparingTo(new BigDecimal("2800000000000.0"));
    assertThat(overview.pe()).isEqualByComparingTo(new BigDecimal("28.5"));
    assertThat(overview.dividendYield()).isEqualByComparingTo(new BigDecimal("0.006"));
    assertThat(overview.beta()).isEqualByComparingTo(new BigDecimal("1.23"));
    assertThat(overview.eps()).isEqualByComparingTo(new BigDecimal("6.05"));
    assertThat(overview.fiftyTwoWeekHigh()).isEqualByComparingTo(new BigDecimal("198.23"));
    assertThat(overview.fiftyTwoWeekLow()).isEqualByComparingTo(new BigDecimal("124.17"));
  }

  @Test
  void getOverviewInvalidatesCrumbAndRetriesOn401() {
    // First call → 401
    server.enqueue(new MockResponse().setResponseCode(401).setBody("Unauthorized"));
    // Retry after crumb refresh → 200
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "quoteSummary": {
                    "result": [{
                      "price": {"shortName": "Apple Inc.", "marketCap": {"raw": 2800000000000.0}},
                      "summaryProfile": {"sector": "Technology"},
                      "summaryDetail": {},
                      "defaultKeyStatistics": {}
                    }],
                    "error": null
                  }
                }
                """));

    when(mockCrumb.getCrumb()).thenReturn("crumb-v1").thenReturn("crumb-v2");

    CompanyOverview overview = provider().getOverview("AAPL");

    assertThat(overview.name()).isEqualTo("Apple Inc.");
    verify(mockCrumb).invalidateCrumb();
    verify(mockCrumb, times(2)).getCrumb();
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void getOverviewThrowsClientExceptionOnNetworkFailure() {
    server.enqueue(
        new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START));

    assertThatThrownBy(() -> provider().getOverview("AAPL"))
        .isInstanceOf(
            com.austinharlan.trading_dashboard.marketdata.MarketDataClientException.class);
  }

  // ── getNews ─────────────────────────────────────────────────────────────────

  @Test
  void getNewsParsesRssFeed() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/rss+xml")
            .setBody(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Yahoo Finance</title>
                    <item>
                      <title>Apple announces new product</title>
                      <link>https://finance.yahoo.com/news/apple-1</link>
                      <description>Apple Inc announced a new product line.</description>
                      <pubDate>Thu, 14 Mar 2024 12:00:00 GMT</pubDate>
                      <guid>urn:apple-article-guid-1</guid>
                    </item>
                  </channel>
                </rss>
                """));

    List<NewsArticle> articles = provider().getNews("AAPL");

    assertThat(articles).hasSize(1);
    assertThat(articles.get(0).headline()).isEqualTo("Apple announces new product");
    assertThat(articles.get(0).url()).isEqualTo("https://finance.yahoo.com/news/apple-1");
    assertThat(articles.get(0).image()).isNull();
    long expectedId = (long) Math.abs(Objects.hashCode("urn:apple-article-guid-1"));
    assertThat(articles.get(0).id()).isEqualTo(expectedId);
  }

  private YahooFinanceMarketDataProvider provider(String... activeProfiles) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(activeProfiles);
    return new YahooFinanceMarketDataProvider(
        WebClient.builder(), properties, new MarketDataQuotaTracker(), mockCrumb, env);
  }
}
