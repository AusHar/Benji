package com.austinharlan.trading_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.austinharlan.trading_dashboard.config.UserContext;
import com.austinharlan.trading_dashboard.persistence.FinanceCategoryRepository;
import com.austinharlan.trading_dashboard.persistence.FinanceTransactionEntity;
import com.austinharlan.trading_dashboard.persistence.FinanceTransactionRepository;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class DefaultFinanceInsightsServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    setUserContext(1L);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void setUserContext(long userId) {
    var ctx = new UserContext(userId, "Test", false, true);
    var auth = new PreAuthenticatedAuthenticationToken(ctx, "", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  void listTransactionsUsesDefaultLimitWhenMissing() {
    FinanceTransactionRepository repository = mock(FinanceTransactionRepository.class);
    when(repository.findAllByUserIdOrderByPostedAtDesc(eq(1L), any(Pageable.class)))
        .thenReturn(List.of());
    DefaultFinanceInsightsService service =
        new DefaultFinanceInsightsService(
            repository, mock(FinanceCategoryRepository.class), mock(UserRepository.class), CLOCK);

    service.listTransactions(null, null);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAllByUserIdOrderByPostedAtDesc(eq(1L), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().isPaged()).isTrue();
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
  }

  @Test
  void listTransactionsCapsLimitAtFiveHundred() {
    FinanceTransactionRepository repository = mock(FinanceTransactionRepository.class);
    when(repository.findAllByUserIdOrderByPostedAtDesc(eq(1L), any(Pageable.class)))
        .thenReturn(List.of());
    DefaultFinanceInsightsService service =
        new DefaultFinanceInsightsService(
            repository, mock(FinanceCategoryRepository.class), mock(UserRepository.class), CLOCK);

    service.listTransactions(9999, null);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAllByUserIdOrderByPostedAtDesc(eq(1L), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().isPaged()).isTrue();
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(500);
  }

  @Test
  void listTransactionsUsesCategoryRepositoryWhenCategoryPresent() {
    FinanceTransactionRepository repository = mock(FinanceTransactionRepository.class);
    when(repository.findByUserIdAndCategoryIgnoreCaseOrderByPostedAtDesc(
            eq(1L), any(), any(Pageable.class)))
        .thenReturn(List.<FinanceTransactionEntity>of());
    DefaultFinanceInsightsService service =
        new DefaultFinanceInsightsService(
            repository, mock(FinanceCategoryRepository.class), mock(UserRepository.class), CLOCK);

    service.listTransactions(20, "groceries");

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository)
        .findByUserIdAndCategoryIgnoreCaseOrderByPostedAtDesc(
            eq(1L), any(), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
  }
}
