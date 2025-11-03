package com.austinharlan.trading_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.finance.FinanceSummaryData;
import com.austinharlan.trading_dashboard.finance.FinanceTransactionRecord;
import com.austinharlan.trading_dashboard.persistence.FinanceTransactionEntity;
import com.austinharlan.trading_dashboard.persistence.FinanceTransactionRepository;
import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DefaultFinanceInsightsServiceIT extends DatabaseIntegrationTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2024-05-15T12:00:00Z"), ZoneOffset.UTC);

  @Autowired private FinanceTransactionRepository repository;

  private DefaultFinanceInsightsService service;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    service = new DefaultFinanceInsightsService(repository, FIXED_CLOCK);
  }

  @AfterEach
  void cleanUp() {
    repository.deleteAll();
  }

  @Test
  void getSummaryAggregatesCurrentMonthTransactions() {
    Instant reference = FIXED_CLOCK.instant();

    FinanceTransactionEntity april =
        new FinanceTransactionEntity(
            "april-tx",
            reference.minus(15, ChronoUnit.DAYS),
            "Rent",
            new BigDecimal("1200.00"),
            "Housing",
            null);

    FinanceTransactionEntity mayOne =
        new FinanceTransactionEntity(
            "may-1",
            reference.minus(13, ChronoUnit.DAYS),
            "Groceries",
            new BigDecimal("50.25"),
            "Groceries",
            null);

    FinanceTransactionEntity mayTwo =
        new FinanceTransactionEntity(
            "may-2",
            reference.minus(5, ChronoUnit.DAYS),
            "Utilities",
            new BigDecimal("75.10"),
            "Bills",
            null);

    FinanceTransactionEntity mayThree =
        new FinanceTransactionEntity(
            "may-3",
            reference.minus(0, ChronoUnit.DAYS),
            "Coffee",
            new BigDecimal("20.00"),
            "Dining",
            null);

    repository.saveAll(List.of(april, mayOne, mayTwo, mayThree));

    FinanceSummaryData summary = service.getSummary();

    assertThat(summary.monthToDateSpend()).isEqualByComparingTo("145.35");
    assertThat(summary.averageDailySpend()).isEqualByComparingTo("9.69");
    assertThat(summary.projectedMonthEndSpend()).isEqualByComparingTo("300.39");
    assertThat(summary.asOf()).isEqualTo(reference);
  }

  @Test
  void listTransactionsFiltersAndLimitsResults() {
    Instant reference = FIXED_CLOCK.instant();

    FinanceTransactionEntity groceriesEarly =
        new FinanceTransactionEntity(
            "groceries-early",
            reference.minus(10, ChronoUnit.DAYS),
            "Grocery Run",
            new BigDecimal("30.00"),
            "Groceries",
            null);

    FinanceTransactionEntity dining =
        new FinanceTransactionEntity(
            "dining",
            reference.minus(3, ChronoUnit.DAYS),
            "Dinner Out",
            new BigDecimal("45.00"),
            "Dining",
            null);

    FinanceTransactionEntity groceriesLatest =
        new FinanceTransactionEntity(
            "groceries-latest",
            reference.minus(1, ChronoUnit.DAYS),
            "Supermarket",
            new BigDecimal("55.00"),
            "Groceries",
            null);

    repository.saveAll(List.of(groceriesEarly, dining, groceriesLatest));

    List<FinanceTransactionRecord> groceries = service.listTransactions(5, "groceries");

    assertThat(groceries)
        .extracting(FinanceTransactionRecord::id)
        .containsExactly("groceries-latest", "groceries-early");

    List<FinanceTransactionRecord> limited = service.listTransactions(2, null);

    assertThat(limited)
        .extracting(FinanceTransactionRecord::id)
        .containsExactly("groceries-latest", "dining");
  }
}
