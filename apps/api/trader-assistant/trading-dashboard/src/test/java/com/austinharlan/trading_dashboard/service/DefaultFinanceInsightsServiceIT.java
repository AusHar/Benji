package com.austinharlan.trading_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.config.UserContext;
import com.austinharlan.trading_dashboard.finance.FinanceSummaryData;
import com.austinharlan.trading_dashboard.finance.FinanceTransactionRecord;
import com.austinharlan.trading_dashboard.persistence.FinanceCategoryRepository;
import com.austinharlan.trading_dashboard.persistence.FinanceTransactionEntity;
import com.austinharlan.trading_dashboard.persistence.FinanceTransactionRepository;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

@SpringBootTest
class DefaultFinanceInsightsServiceIT extends DatabaseIntegrationTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2024-05-15T12:00:00Z"), ZoneOffset.UTC);

  @Autowired private FinanceTransactionRepository repository;
  @Autowired private FinanceCategoryRepository categoryRepository;
  @Autowired private UserRepository userRepository;

  private DefaultFinanceInsightsService service;
  private Long testUserId;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    testUserId = userRepository.findByApiKey("test-api-key").orElseThrow().getId();
    service =
        new DefaultFinanceInsightsService(
            repository, categoryRepository, userRepository, FIXED_CLOCK);
    setUserContext(testUserId);
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    repository.deleteAll();
  }

  private void setUserContext(long userId) {
    var ctx = new UserContext(userId, "Test", false, true);
    var auth = new PreAuthenticatedAuthenticationToken(ctx, "", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  void getSummaryAggregatesCurrentMonthTransactions() {
    Instant reference = FIXED_CLOCK.instant();

    FinanceTransactionEntity april =
        new FinanceTransactionEntity(
            "april-tx",
            testUserId,
            reference.minus(15, ChronoUnit.DAYS),
            "Rent",
            new BigDecimal("1200.00"),
            "Housing",
            null);

    FinanceTransactionEntity mayOne =
        new FinanceTransactionEntity(
            "may-1",
            testUserId,
            reference.minus(13, ChronoUnit.DAYS),
            "Groceries",
            new BigDecimal("50.25"),
            "Groceries",
            null);

    FinanceTransactionEntity mayTwo =
        new FinanceTransactionEntity(
            "may-2",
            testUserId,
            reference.minus(5, ChronoUnit.DAYS),
            "Utilities",
            new BigDecimal("75.10"),
            "Bills",
            null);

    FinanceTransactionEntity mayThree =
        new FinanceTransactionEntity(
            "may-3",
            testUserId,
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
            testUserId,
            reference.minus(10, ChronoUnit.DAYS),
            "Grocery Run",
            new BigDecimal("30.00"),
            "Groceries",
            null);

    FinanceTransactionEntity dining =
        new FinanceTransactionEntity(
            "dining",
            testUserId,
            reference.minus(3, ChronoUnit.DAYS),
            "Dinner Out",
            new BigDecimal("45.00"),
            "Dining",
            null);

    FinanceTransactionEntity groceriesLatest =
        new FinanceTransactionEntity(
            "groceries-latest",
            testUserId,
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
