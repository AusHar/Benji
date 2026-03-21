package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class RealMarketDataProviderTest {
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private MockWebServer server;
  private MockWebServer crumbServer;
  private MarketDataProperties properties;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    crumbServer = new MockWebServer();
    crumbServer.start();

    properties = new MarketDataProperties();
    properties.setQuery2BaseUrl(server.url("/").toString());
    properties.setYahooRssBaseUrl(server.url("/").toString());
    properties.setHealthSymbol("AAPL");
    properties.setConnectTimeout(TIMEOUT);
    properties.setReadTimeout(TIMEOUT);
    properties.setWriteTimeout(TIMEOUT);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
    crumbServer.shutdown();
  }

  @Test
  void shouldReturnQuoteFromYahooQuoteSummary() {
    server.enqueue(
        jsonResponse(
            """
            {
              "quoteSummary": {
                "result": [{
                  "price": {
                    "symbol": "AAPL",
                    "shortName": "Apple Inc.",
                    "regularMarketPrice": {"raw": 189.84},
                    "regularMarketChangePercent": {"raw": 1.25},
                    "regularMarketTime": 1700000000,
                    "marketCap": {"raw": 2950000000000}
                  }
                }]
              }
            }
            """));

    RealMarketDataProvider provider = provider();
    Quote quote = provider.getQuote("AAPL");

    assertThat(quote.symbol()).isEqualTo("AAPL");
    assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("189.84"));
    assertThat(quote.changePercent()).isEqualByComparingTo(new BigDecimal("1.25"));
    assertThat(quote.timestamp()).isEqualTo(Instant.ofEpochSecond(1700000000L));
  }

  @Test
  void shouldThrowWhenYahooReturnsServerError() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));

    RealMarketDataProvider provider = provider();

    assertThatThrownBy(() -> provider.getQuote("AAPL"))
        .isInstanceOf(MarketDataClientException.class);
  }

  @Test
  void shouldThrowQuoteNotFoundWhenNoResults() {
    server.enqueue(jsonResponse("""
        {"quoteSummary": {"result": []}}
        """));

    RealMarketDataProvider provider = provider();

    assertThatThrownBy(() -> provider.getQuote("INVALID"))
        .isInstanceOf(QuoteNotFoundException.class);
  }

  @Test
  void shouldReturnOverviewFromMultipleModules() {
    server.enqueue(
        jsonResponse(
            """
            {
              "quoteSummary": {
                "result": [{
                  "price": {
                    "shortName": "Apple Inc.",
                    "marketCap": {"raw": 2950000000000}
                  },
                  "assetProfile": {
                    "sector": "Technology",
                    "industry": "Consumer Electronics"
                  },
                  "summaryDetail": {
                    "trailingPE": {"raw": 29.48},
                    "dividendYield": {"raw": 0.0055},
                    "fiftyTwoWeekHigh": {"raw": 199.62},
                    "fiftyTwoWeekLow": {"raw": 124.17}
                  },
                  "defaultKeyStatistics": {
                    "trailingEps": {"raw": 6.26},
                    "beta": {"raw": 1.2}
                  }
                }]
              }
            }
            """));

    RealMarketDataProvider provider = provider();
    CompanyOverview overview = provider.getOverview("AAPL");

    assertThat(overview.symbol()).isEqualTo("AAPL");
    assertThat(overview.name()).isEqualTo("Apple Inc.");
    assertThat(overview.sector()).isEqualTo("Technology");
    assertThat(overview.industry()).isEqualTo("Consumer Electronics");
    assertThat(overview.marketCap()).isEqualByComparingTo(new BigDecimal("2950000000000"));
    assertThat(overview.pe()).isEqualByComparingTo(new BigDecimal("29.48"));
    assertThat(overview.eps()).isEqualByComparingTo(new BigDecimal("6.26"));
    assertThat(overview.beta()).isEqualByComparingTo(new BigDecimal("1.2"));
  }

  @Test
  void shouldReturnHistoryFromChartEndpoint() {
    server.enqueue(
        jsonResponse(
            """
            {
              "chart": {
                "result": [{
                  "timestamp": [1690848000, 1690934400],
                  "indicators": {
                    "quote": [{
                      "open": [149.50, 151.00],
                      "high": [151.00, 153.00],
                      "low": [149.00, 151.50],
                      "close": [150.10, 152.50],
                      "volume": [34000000, 28000000]
                    }]
                  }
                }]
              }
            }
            """));

    RealMarketDataProvider provider = provider();
    List<DailyBar> bars = provider.getDailyHistory("AAPL");

    assertThat(bars).hasSize(2);
    assertThat(bars.get(0).close()).isEqualByComparingTo(new BigDecimal("150.1"));
    assertThat(bars.get(1).close()).isEqualByComparingTo(new BigDecimal("152.5"));
    assertThat(bars.get(0).date()).isBefore(bars.get(1).date());
  }

  @Test
  void shouldReturnEmptyNewsOnRssError() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("error"));

    RealMarketDataProvider provider = provider();
    List<NewsArticle> news = provider.getNews("AAPL");

    assertThat(news).isEmpty();
  }

  @Test
  void shouldParseRssNewsEntries() {
    String rssXml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <item>
              <title>Apple stock rises</title>
              <link>https://finance.yahoo.com/news/apple-rises</link>
              <description>Apple Inc shares gained today.</description>
              <pubDate>Thu, 01 Aug 2024 12:00:00 GMT</pubDate>
              <guid>https://finance.yahoo.com/news/apple-rises</guid>
            </item>
          </channel>
        </rss>
        """;
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/xml")
            .setBody(rssXml));

    RealMarketDataProvider provider = provider();
    List<NewsArticle> news = provider.getNews("AAPL");

    assertThat(news).hasSize(1);
    assertThat(news.get(0).headline()).isEqualTo("Apple stock rises");
    assertThat(news.get(0).source()).isEqualTo("Yahoo Finance");
    assertThat(news.get(0).url()).isEqualTo("https://finance.yahoo.com/news/apple-rises");
  }

  private RealMarketDataProvider provider() {
    YahooCrumbProvider stubCrumb = new YahooCrumbProvider(crumbServer.url("/").toString());
    crumbServer.enqueue(
        new MockResponse().setResponseCode(200).addHeader("Set-Cookie", "A3=d=test; Path=/"));
    crumbServer.enqueue(new MockResponse().setResponseCode(200).setBody("test-crumb"));
    return new RealMarketDataProvider(WebClient.builder(), properties, stubCrumb);
  }

  private static MockResponse jsonResponse(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
