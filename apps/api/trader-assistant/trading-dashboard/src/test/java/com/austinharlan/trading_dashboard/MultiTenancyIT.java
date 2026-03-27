package com.austinharlan.trading_dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.persistence.*;
import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultiTenancyIT extends DatabaseIntegrationTest {

  @Autowired TestRestTemplate rest;
  @Autowired UserRepository userRepository;
  @Autowired PortfolioPositionRepository portfolioRepository;

  private String userAKey;
  private String userBKey;

  @BeforeEach
  void setup() {
    HttpHeaders admin = new HttpHeaders();
    admin.set("X-API-KEY", "test-api-key");
    admin.setContentType(MediaType.APPLICATION_JSON);

    var respA =
        rest.exchange(
            "/api/admin/users",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("displayName", "Alice"), admin),
            Map.class);
    userAKey = (String) respA.getBody().get("apiKey");

    var respB =
        rest.exchange(
            "/api/admin/users",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("displayName", "Bob"), admin),
            Map.class);
    userBKey = (String) respB.getBody().get("apiKey");
  }

  @org.junit.jupiter.api.AfterEach
  @org.springframework.transaction.annotation.Transactional
  void cleanup() {
    if (userAKey != null) {
      userRepository
          .findByApiKey(userAKey)
          .ifPresent(u -> portfolioRepository.deleteAllByUserId(u.getId()));
    }
    if (userBKey != null) {
      userRepository
          .findByApiKey(userBKey)
          .ifPresent(u -> portfolioRepository.deleteAllByUserId(u.getId()));
    }
  }

  @Test
  void usersOnlySeeTheirOwnPositions() {
    HttpHeaders hA = headers(userAKey);
    rest.exchange(
        "/api/portfolio/positions",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("ticker", "AAPL", "quantity", 10, "price_per_share", 150.0), hA),
        Map.class);

    HttpHeaders hB = headers(userBKey);
    rest.exchange(
        "/api/portfolio/positions",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("ticker", "GOOG", "quantity", 5, "price_per_share", 170.0), hB),
        Map.class);

    ResponseEntity<Map> aliceResp =
        rest.exchange("/api/portfolio/positions", HttpMethod.GET, new HttpEntity<>(hA), Map.class);
    assertThat((List<?>) aliceResp.getBody().get("positions")).hasSize(1);

    ResponseEntity<Map> bobResp =
        rest.exchange("/api/portfolio/positions", HttpMethod.GET, new HttpEntity<>(hB), Map.class);
    assertThat((List<?>) bobResp.getBody().get("positions")).hasSize(1);
  }

  private HttpHeaders headers(String key) {
    HttpHeaders h = new HttpHeaders();
    h.set("X-API-KEY", key);
    h.setContentType(MediaType.APPLICATION_JSON);
    return h;
  }
}
