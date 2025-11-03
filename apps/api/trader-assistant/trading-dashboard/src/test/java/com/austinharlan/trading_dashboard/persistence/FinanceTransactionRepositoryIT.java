package com.austinharlan.trading_dashboard.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;

@SpringBootTest
class FinanceTransactionRepositoryIT extends DatabaseIntegrationTest {

  @Autowired private FinanceTransactionRepository repository;

  @AfterEach
  void resetDatabase() {
    repository.deleteAll();
  }

  @Test
  void flywayMigrationsRunOnStartup() {
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void saveAndQueryTransactions() {
    Instant now = Instant.parse("2024-05-15T10:15:30Z");

    FinanceTransactionEntity groceries =
        new FinanceTransactionEntity(
            "groceries-1",
            now.minus(2, ChronoUnit.DAYS),
            "Trader Joe's",
            new BigDecimal("45.20"),
            "Groceries",
            null);

    FinanceTransactionEntity dining =
        new FinanceTransactionEntity(
            "dining-1",
            now.minus(1, ChronoUnit.DAYS),
            "Neighborhood Bistro",
            new BigDecimal("62.10"),
            "Dining",
            "Date night");

    FinanceTransactionEntity groceriesLater =
        new FinanceTransactionEntity(
            "groceries-2",
            now,
            "Whole Foods",
            new BigDecimal("85.75"),
            "Groceries",
            null);

    repository.saveAll(List.of(groceries, dining, groceriesLater));

    List<FinanceTransactionEntity> groceriesResults =
        repository.findByCategoryIgnoreCaseOrderByPostedAtDesc("groceries", Pageable.unpaged());

    assertThat(groceriesResults)
        .extracting(FinanceTransactionEntity::getId)
        .containsExactly("groceries-2", "groceries-1");

    List<FinanceTransactionEntity> rangeResults =
        repository.findWithinRange(
            now.minus(3, ChronoUnit.DAYS), now.plus(1, ChronoUnit.MINUTES));

    assertThat(rangeResults)
        .hasSize(3)
        .extracting(FinanceTransactionEntity::getId)
        .contains("groceries-1", "dining-1", "groceries-2");
  }
}
