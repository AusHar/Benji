package com.austinharlan.trading_dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.persistence.PortfolioPositionRepository;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoIT extends DatabaseIntegrationTest {

  @Autowired TestRestTemplate rest;
  @Autowired UserRepository userRepository;
  @Autowired PortfolioPositionRepository portfolioRepository;

  @Test
  @SuppressWarnings("unchecked")
  void postDemoSession_returns200WithApiKey() {
    ResponseEntity<Map> response = rest.postForEntity("/api/demo/session", null, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("apiKey", "demo");
  }

  @Test
  void postDemoSession_seedsDemoData() {
    rest.postForEntity("/api/demo/session", null, Map.class);

    Long demoUserId = userRepository.findByIsDemoTrue().orElseThrow().getId();
    assertThat(portfolioRepository.findAllByUserId(demoUserId)).hasSize(10);
  }

  @Test
  void postDemoSession_isIdempotent() {
    rest.postForEntity("/api/demo/session", null, Map.class);
    rest.postForEntity("/api/demo/session", null, Map.class);

    Long demoUserId = userRepository.findByIsDemoTrue().orElseThrow().getId();
    assertThat(portfolioRepository.findAllByUserId(demoUserId)).hasSize(10);
  }
}
