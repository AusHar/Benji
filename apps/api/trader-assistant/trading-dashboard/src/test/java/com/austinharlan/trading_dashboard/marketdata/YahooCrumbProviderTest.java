package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YahooCrumbProviderTest {
  private MockWebServer server;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void fetchesCookieAndCrumbOnFirstCall() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "A3=d=session123; Path=/; Domain=.yahoo.com"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("test-crumb-value"));

    YahooCrumbProvider provider = new YahooCrumbProvider(server.url("/").toString());

    assertThat(provider.getCrumb()).isEqualTo("test-crumb-value");
    assertThat(provider.getCookie()).contains("A3=d=session123");
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void cachesCrumbOnSubsequentCalls() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "A3=d=session123; Path=/; Domain=.yahoo.com"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("cached-crumb"));

    YahooCrumbProvider provider = new YahooCrumbProvider(server.url("/").toString());

    String first = provider.getCrumb();
    String second = provider.getCrumb();

    assertThat(first).isEqualTo("cached-crumb");
    assertThat(second).isEqualTo("cached-crumb");
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void invalidateForcesFreshFetch() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "A3=d=first; Path=/; Domain=.yahoo.com"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("crumb-1"));
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "A3=d=second; Path=/; Domain=.yahoo.com"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("crumb-2"));

    YahooCrumbProvider provider = new YahooCrumbProvider(server.url("/").toString());

    assertThat(provider.getCrumb()).isEqualTo("crumb-1");
    provider.invalidate();
    assertThat(provider.getCrumb()).isEqualTo("crumb-2");
    assertThat(server.getRequestCount()).isEqualTo(4);
  }

  @Test
  void throwsWhenCookieFetchFails() {
    server.enqueue(new MockResponse().setResponseCode(500));

    YahooCrumbProvider provider = new YahooCrumbProvider(server.url("/").toString());

    assertThatThrownBy(provider::getCrumb).isInstanceOf(MarketDataClientException.class);
  }

  @Test
  void throwsWhenCrumbFetchFails() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "A3=d=session; Path=/; Domain=.yahoo.com"));
    server.enqueue(new MockResponse().setResponseCode(403));

    YahooCrumbProvider provider = new YahooCrumbProvider(server.url("/").toString());

    assertThatThrownBy(provider::getCrumb).isInstanceOf(MarketDataClientException.class);
  }
}
