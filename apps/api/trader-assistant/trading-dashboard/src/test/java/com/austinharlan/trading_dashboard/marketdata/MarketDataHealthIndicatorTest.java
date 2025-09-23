package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class MarketDataHealthIndicatorTest {
  private static MockWebServer server;

  @Autowired private HealthEndpoint healthEndpoint;

  @BeforeAll
  static void startServer() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterAll
  static void shutdownServer() throws Exception {
    server.shutdown();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "trading.marketdata.base-url",
        () -> server.url("/").toString());
    registry.add("trading.marketdata.api-key", () -> "test-key");
    registry.add("trading.marketdata.health-symbol", () -> "AAPL");
    registry.add("trading.marketdata.connect-timeout", () -> "1s");
    registry.add("trading.marketdata.read-timeout", () -> "1s");
    registry.add("trading.marketdata.write-timeout", () -> "1s");
  }

  @Test
  void shouldReportUpWhenProviderResponds() {
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
                    "07. latest trading day": "2024-10-01"
                  }
                }
                """));

    HealthComponent component = healthEndpoint.healthForPath("marketData");

    assertThat(component.getStatus()).isEqualTo(Status.UP);
    Health health = (Health) component;
    assertThat(health.getDetails()).containsEntry("symbol", "AAPL");
  }

  @Test
  void shouldReportDownWhenProviderFails() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(500)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"error\":\"internal\"}"));

    HealthComponent component = healthEndpoint.healthForPath("marketData");

    assertThat(component.getStatus()).isEqualTo(Status.DOWN);
  }
}
