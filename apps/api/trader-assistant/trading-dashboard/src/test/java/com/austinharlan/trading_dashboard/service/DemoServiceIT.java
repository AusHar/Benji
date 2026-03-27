package com.austinharlan.trading_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.persistence.*;
import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoServiceIT extends DatabaseIntegrationTest {

  @Autowired DemoService demoService;
  @Autowired UserRepository userRepository;
  @Autowired PortfolioPositionRepository portfolioRepository;
  @Autowired TradeRepository tradeRepository;
  @Autowired FinanceTransactionRepository financeRepository;
  @Autowired JournalEntryRepository journalEntryRepository;
  @Autowired JournalGoalRepository journalGoalRepository;

  @Test
  void resetDemoData_seedsAllTables() {
    demoService.resetDemoData();

    Long demoUserId = userRepository.findByIsDemoTrue().orElseThrow().getId();
    assertThat(portfolioRepository.findAllByUserId(demoUserId)).hasSize(10);
    assertThat(tradeRepository.findAllByUserIdOrderByTradeDateDescCreatedAtDesc(demoUserId))
        .hasSizeGreaterThanOrEqualTo(15);
    assertThat(
            financeRepository.findAllByUserIdOrderByPostedAtDesc(
                demoUserId, org.springframework.data.domain.Pageable.unpaged()))
        .hasSizeGreaterThanOrEqualTo(20);
    assertThat(journalEntryRepository.findAllByUserIdOrderByEntryDateDesc(demoUserId)).hasSize(4);
    assertThat(journalGoalRepository.findAllByUserId(demoUserId)).isEmpty();
  }

  @Test
  void resetDemoData_isIdempotent() {
    demoService.resetDemoData();
    demoService.resetDemoData();

    Long demoUserId = userRepository.findByIsDemoTrue().orElseThrow().getId();
    assertThat(portfolioRepository.findAllByUserId(demoUserId)).hasSize(10);
  }

  @Test
  void resetDemoData_doesNotAffectOtherUsers() {
    Long testUserId = userRepository.findByApiKey("test-api-key").orElseThrow().getId();
    portfolioRepository.save(
        new PortfolioPositionEntity(
            testUserId, "ZZTEST", java.math.BigDecimal.ONE, java.math.BigDecimal.TEN));
    tradeRepository.save(
        new TradeEntity(
            testUserId,
            "ZZTEST",
            "BUY",
            java.math.BigDecimal.ONE,
            java.math.BigDecimal.TEN,
            java.time.LocalDate.now(),
            "test trade"));

    demoService.resetDemoData();

    assertThat(portfolioRepository.findByUserIdAndTicker(testUserId, "ZZTEST")).isPresent();
    assertThat(tradeRepository.findAllByUserIdOrderByTradeDateDescCreatedAtDesc(testUserId))
        .hasSize(1);
  }
}
