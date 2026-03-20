package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

class CoinGeckoMarketDataProviderTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private MockWebServer server;
  private MarketDataProperties properties;

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
    properties.setCryptoSymbols(Map.of("BTC", "bitcoin", "ETH", "ethereum"));
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
  void getQuoteParsesResponse() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "bitcoin": {
                    "usd": 42000.0,
                    "usd_24h_change": 1.5,
                    "usd_last_updated_at": 1709900400
                  }
                }
                """));

    Quote quote = provider().getQuote("bitcoin");

    assertThat(quote.symbol()).isEqualTo("bitcoin");
    assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("42000.0"));
    assertThat(quote.changePercent()).isEqualByComparingTo(new BigDecimal("1.5"));
  }

  @Test
  void getQuoteThrowsOnEmptyResponse() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{}"));

    assertThatThrownBy(() -> provider().getQuote("unknowncoin"))
        .isInstanceOf(QuoteNotFoundException.class);
  }

  @Test
  void getQuoteThrowsRateLimitOn429() {
    server.enqueue(new MockResponse().setResponseCode(429).setBody("{}"));

    assertThatThrownBy(() -> provider().getQuote("bitcoin"))
        .isInstanceOf(MarketDataRateLimitException.class);
  }

  @Test
  void getDailyHistoryParsesOhlcTimestampFromMs() {
    // CoinGecko timestamps are in milliseconds
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                [
                  [1690848000000, 29000.0, 29500.0, 28800.0, 29200.0],
                  [1690934400000, 29200.0, 29800.0, 29100.0, 29600.0]
                ]
                """));

    List<DailyBar> bars = provider().getDailyHistory("bitcoin");

    assertThat(bars).hasSize(2);
    assertThat(bars.get(0).close()).isEqualByComparingTo(new BigDecimal("29200.0"));
    assertThat(bars.get(1).close()).isEqualByComparingTo(new BigDecimal("29600.0"));
    assertThat(bars.get(0).date()).isBefore(bars.get(1).date());
  }

  @Test
  void getOverviewParsesFields() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "name": "Bitcoin",
                  "market_data": {
                    "market_cap": {"usd": 820000000000.0},
                    "ath":        {"usd": 69000.0},
                    "atl":        {"usd": 67.81}
                  }
                }
                """));

    CompanyOverview overview = provider().getOverview("bitcoin");

    assertThat(overview.name()).isEqualTo("Bitcoin");
    assertThat(overview.sector()).isEqualTo("Cryptocurrency");
    assertThat(overview.marketCap()).isEqualByComparingTo(new BigDecimal("820000000000.0"));
    assertThat(overview.fiftyTwoWeekHigh()).isEqualByComparingTo(new BigDecimal("69000.0"));
    assertThat(overview.pe()).isNull();
    assertThat(overview.eps()).isNull();
    assertThat(overview.dividendYield()).isNull();
  }

  @Test
  void getNewsFetchesYahooRssWithDashUsdSuffix() throws InterruptedException {
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
                      <title>Bitcoin surges past $42K</title>
                      <link>https://finance.yahoo.com/news/btc-1</link>
                      <description>Bitcoin hit new highs.</description>
                      <pubDate>Thu, 14 Mar 2024 12:00:00 GMT</pubDate>
                      <guid>urn:btc-article-1</guid>
                    </item>
                  </channel>
                </rss>
                """));

    CryptoSymbolMapper mapper = mapper();
    List<NewsArticle> articles = provider(mapper).getNews("BTC");

    assertThat(articles).hasSize(1);
    assertThat(articles.get(0).headline()).isEqualTo("Bitcoin surges past $42K");

    // Verify the RSS URL used BTC-USD
    String path = server.takeRequest().getPath();
    assertThat(path).contains("BTC-USD");
  }

  private CoinGeckoMarketDataProvider provider() {
    return provider(mapper());
  }

  private CoinGeckoMarketDataProvider provider(CryptoSymbolMapper mapper) {
    return new CoinGeckoMarketDataProvider(
        WebClient.builder(),
        properties,
        new MarketDataQuotaTracker(),
        mapper,
        new MockEnvironment());
  }

  private CryptoSymbolMapper mapper() {
    return new CryptoSymbolMapper(properties);
  }
}
