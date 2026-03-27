package com.austinharlan.trading_dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.persistence.UserRepository;
import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminIT extends DatabaseIntegrationTest {

  @Autowired TestRestTemplate rest;
  @Autowired UserRepository userRepository;

  private HttpHeaders adminHeaders() {
    HttpHeaders h = new HttpHeaders();
    h.set("X-API-KEY", "test-api-key");
    h.setContentType(MediaType.APPLICATION_JSON);
    return h;
  }

  private HttpHeaders demoHeaders() {
    HttpHeaders h = new HttpHeaders();
    h.set("X-API-KEY", "demo");
    h.setContentType(MediaType.APPLICATION_JSON);
    return h;
  }

  @Test
  @SuppressWarnings("unchecked")
  void createUser_withAdminKey_returns201() {
    var body = Map.of("displayName", "Jake");
    var request = new HttpEntity<>(body, adminHeaders());

    ResponseEntity<Map> response =
        rest.exchange("/api/admin/users", HttpMethod.POST, request, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsKeys("id", "displayName", "apiKey");
    assertThat(response.getBody().get("displayName")).isEqualTo("Jake");
    assertThat((String) response.getBody().get("apiKey")).isNotBlank();
  }

  @Test
  void createUser_withDemoKey_returns403() {
    var body = Map.of("displayName", "Hacker");
    var request = new HttpEntity<>(body, demoHeaders());

    ResponseEntity<Map> response =
        rest.exchange("/api/admin/users", HttpMethod.POST, request, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void createUser_withNoKey_returns401() {
    var body = Map.of("displayName", "Nobody");
    var request = new HttpEntity<>(body);

    ResponseEntity<Map> response =
        rest.exchange("/api/admin/users", HttpMethod.POST, request, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
