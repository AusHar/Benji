package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.io.IOException;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class YahooCrumbProviderTest {

  private MockWebServer server;
  private YahooCrumbProvider provider;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    // Point all URL fields to the same MockWebServer for simplicity —
    // MockWebServer returns queued responses in order regardless of path
    String url = server.url("/").toString();
    MarketDataProperties properties = new MarketDataProperties();
    properties.setBaseUrl(url);
    properties.setQuery2BaseUrl(url);
    properties.setYahooRssBaseUrl(url);
    properties.setCoinGeckoBaseUrl(url);
    properties.setConnectTimeout(Duration.ofSeconds(2));
    properties.setReadTimeout(Duration.ofSeconds(2));
    properties.setWriteTimeout(Duration.ofSeconds(2));

    provider = new YahooCrumbProvider(WebClient.builder(), properties);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void getCrumbFetchesCookieThenCrumb() throws InterruptedException {
    server.enqueue(
        new MockResponse().setResponseCode(200).addHeader("Set-Cookie", "A=1; Path=/; Secure"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("test-crumb-abc123"));

    String crumb = provider.getCrumb();

    assertThat(crumb).isEqualTo("test-crumb-abc123");
    assertThat(server.getRequestCount()).isEqualTo(2);

    server.takeRequest(); // home page request
    RecordedRequest crumbRequest = server.takeRequest();
    assertThat(crumbRequest.getHeader("Cookie")).contains("A=1");
  }

  @Test
  void getCrumbReturnsCachedValueWithoutHittingServer() {
    server.enqueue(new MockResponse().setResponseCode(200).addHeader("Set-Cookie", "A=1; Path=/"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("cached-crumb"));

    String first = provider.getCrumb();
    String second = provider.getCrumb();

    assertThat(first).isEqualTo("cached-crumb");
    assertThat(second).isEqualTo("cached-crumb");
    // Only 2 HTTP requests total, not 4
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void invalidateCrumbForcesFreshFetchOnNextCall() {
    // First fetch
    server.enqueue(new MockResponse().setResponseCode(200).addHeader("Set-Cookie", "A=1; Path=/"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("crumb-v1"));
    provider.getCrumb();

    provider.invalidateCrumb();

    // Second fetch after invalidation
    server.enqueue(new MockResponse().setResponseCode(200).addHeader("Set-Cookie", "A=2; Path=/"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("crumb-v2"));
    String refreshed = provider.getCrumb();

    assertThat(refreshed).isEqualTo("crumb-v2");
    assertThat(server.getRequestCount()).isEqualTo(4);
  }
}
