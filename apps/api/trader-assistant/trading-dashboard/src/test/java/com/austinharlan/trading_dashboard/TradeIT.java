package com.austinharlan.trading_dashboard;

import static org.assertj.core.api.Assertions.*;

import com.austinharlan.trading_dashboard.persistence.TradeEntity;
import com.austinharlan.trading_dashboard.persistence.TradeRepository;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TradeIT extends DatabaseIntegrationTest {

  @Autowired private TestRestTemplate rest;
  @Autowired private TradeRepository tradeRepository;
  @Autowired private UserRepository userRepository;

  private final HttpHeaders headers = new HttpHeaders();
  private Long testUserId;

  {
    headers.set("X-API-KEY", "test-api-key");
    headers.setContentType(MediaType.APPLICATION_JSON);
  }

  @BeforeEach
  void setUp() {
    testUserId = userRepository.findByApiKey("test-api-key").orElseThrow().getId();
  }

  @AfterEach
  void cleanup() {
    tradeRepository.deleteAllByUserId(testUserId);
  }

  @Test
  void postTrade_returns201AndPersists() {
    String body =
        """
        {"ticker":"AAPL","side":"BUY","quantity":10,"pricePerShare":150.0,"tradeDate":"2026-03-01"}
        """;

    ResponseEntity<Map> response =
        rest.exchange("/api/trades", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsEntry("ticker", "AAPL");
    assertThat(tradeRepository.count()).isEqualTo(1);
  }

  @Test
  void getTrades_returnsSortedByDateDesc() {
    tradeRepository.save(
        new TradeEntity(
            testUserId,
            "AAPL",
            "BUY",
            BigDecimal.TEN,
            BigDecimal.valueOf(100),
            LocalDate.of(2026, 1, 1),
            null));
    tradeRepository.save(
        new TradeEntity(
            testUserId,
            "MSFT",
            "BUY",
            BigDecimal.TEN,
            BigDecimal.valueOf(200),
            LocalDate.of(2026, 3, 1),
            null));

    ResponseEntity<Map> response =
        rest.exchange("/api/trades", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<Map> trades = (List<Map>) response.getBody().get("trades");
    assertThat(trades).hasSize(2);
    assertThat(trades.get(0).get("ticker")).isEqualTo("MSFT"); // newer first
  }

  @Test
  void getTrades_filtersByTicker() {
    tradeRepository.save(
        new TradeEntity(
            testUserId,
            "AAPL",
            "BUY",
            BigDecimal.TEN,
            BigDecimal.valueOf(100),
            LocalDate.of(2026, 1, 1),
            null));
    tradeRepository.save(
        new TradeEntity(
            testUserId,
            "MSFT",
            "BUY",
            BigDecimal.TEN,
            BigDecimal.valueOf(200),
            LocalDate.of(2026, 1, 1),
            null));

    ResponseEntity<Map> response =
        rest.exchange(
            "/api/trades?ticker=AAPL", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

    List<Map> trades = (List<Map>) response.getBody().get("trades");
    assertThat(trades).hasSize(1);
    assertThat(trades.get(0).get("ticker")).isEqualTo("AAPL");
  }

  @Test
  void deleteTrade_returns204() {
    TradeEntity saved =
        tradeRepository.save(
            new TradeEntity(
                testUserId,
                "AAPL",
                "BUY",
                BigDecimal.TEN,
                BigDecimal.valueOf(100),
                LocalDate.of(2026, 1, 1),
                null));

    ResponseEntity<Void> response =
        rest.exchange(
            "/api/trades/" + saved.getId(),
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(tradeRepository.count()).isEqualTo(0);
  }

  @Test
  void closedTrades_returnsFifoMatched() {
    tradeRepository.save(
        new TradeEntity(
            testUserId,
            "AAPL",
            "BUY",
            BigDecimal.TEN,
            BigDecimal.valueOf(100),
            LocalDate.of(2026, 1, 1),
            null));
    tradeRepository.save(
        new TradeEntity(
            testUserId,
            "AAPL",
            "SELL",
            BigDecimal.TEN,
            BigDecimal.valueOf(150),
            LocalDate.of(2026, 2, 1),
            null));

    ResponseEntity<Map> response =
        rest.exchange("/api/trades/closed", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<Map> closed = (List<Map>) response.getBody().get("closedTrades");
    assertThat(closed).hasSize(1);
    assertThat(((Number) closed.get(0).get("pnl")).doubleValue()).isCloseTo(500.0, within(0.01));
  }

  @Test
  void stats_returnsAggregatedData() {
    tradeRepository.save(
        new TradeEntity(
            testUserId,
            "AAPL",
            "BUY",
            BigDecimal.TEN,
            BigDecimal.valueOf(100),
            LocalDate.of(2026, 1, 1),
            null));
    tradeRepository.save(
        new TradeEntity(
            testUserId,
            "AAPL",
            "SELL",
            BigDecimal.TEN,
            BigDecimal.valueOf(150),
            LocalDate.of(2026, 2, 1),
            null));

    ResponseEntity<Map> response =
        rest.exchange("/api/trades/stats", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(((Number) response.getBody().get("wins")).intValue()).isEqualTo(1);
    assertThat(((Number) response.getBody().get("winRate")).doubleValue())
        .isCloseTo(100.0, within(0.1));
  }

  @Test
  void postOptionTrade_returns201WithOptionFields() {
    String body =
        """
        {"ticker":"AAPL","side":"BUY","quantity":1,"pricePerShare":5.00,
         "tradeDate":"2026-03-01","assetType":"OPTION","optionType":"CALL",
         "strikePrice":200.0,"expirationDate":"2026-04-18"}
        """;

    ResponseEntity<Map> response =
        rest.exchange("/api/trades", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsEntry("assetType", "OPTION");
    assertThat(response.getBody()).containsEntry("optionType", "CALL");
    assertThat(((Number) response.getBody().get("strikePrice")).doubleValue())
        .isCloseTo(200.0, within(0.01));
    assertThat(((Number) response.getBody().get("multiplier")).intValue()).isEqualTo(100);
  }

  @Test
  void closedOptionTrades_includesMultiplierInPnl() {
    // BUY 1 AAPL $200 CALL @ $5
    tradeRepository.save(
        new TradeEntity(
            testUserId,
            "AAPL",
            "BUY",
            BigDecimal.ONE,
            BigDecimal.valueOf(5),
            LocalDate.of(2026, 1, 1),
            null,
            "OPTION",
            "CALL",
            BigDecimal.valueOf(200),
            LocalDate.of(2026, 4, 18),
            100));
    // SELL 1 AAPL $200 CALL @ $8
    tradeRepository.save(
        new TradeEntity(
            testUserId,
            "AAPL",
            "SELL",
            BigDecimal.ONE,
            BigDecimal.valueOf(8),
            LocalDate.of(2026, 2, 1),
            null,
            "OPTION",
            "CALL",
            BigDecimal.valueOf(200),
            LocalDate.of(2026, 4, 18),
            100));

    ResponseEntity<Map> response =
        rest.exchange("/api/trades/closed", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<Map> closed = (List<Map>) response.getBody().get("closedTrades");
    assertThat(closed).hasSize(1);
    assertThat(((Number) closed.get(0).get("pnl")).doubleValue())
        .isCloseTo(300.0, within(0.01)); // (8-5) * 1 * 100
    assertThat(closed.get(0).get("assetType")).isEqualTo("OPTION");
  }

  @Test
  void equityTrades_stillWorkUnchanged() {
    // Existing equity flow should be backward compatible
    String body =
        """
        {"ticker":"MSFT","side":"BUY","quantity":5,"pricePerShare":400.0,"tradeDate":"2026-03-01"}
        """;

    ResponseEntity<Map> response =
        rest.exchange("/api/trades", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsEntry("assetType", "EQUITY");
    assertThat(((Number) response.getBody().get("multiplier")).intValue()).isEqualTo(1);
  }

  @Test
  void postOptionTrade_rejectsExpireOnEquity() {
    String body = """
        {"ticker":"AAPL","side":"EXPIRE","assetType":"EQUITY"}
        """;

    ResponseEntity<Map> response =
        rest.exchange("/api/trades", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
