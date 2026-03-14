package com.austinharlan.trading_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.austinharlan.trading_dashboard.persistence.FinanceTransactionEntity;
import com.austinharlan.trading_dashboard.persistence.FinanceTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class DefaultFinanceInsightsServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void listTransactionsUsesDefaultLimitWhenMissing() {
    FinanceTransactionRepository repository = mock(FinanceTransactionRepository.class);
    when(repository.findAllByOrderByPostedAtDesc(any(Pageable.class))).thenReturn(List.of());
    DefaultFinanceInsightsService service = new DefaultFinanceInsightsService(repository, CLOCK);

    service.listTransactions(null, null);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAllByOrderByPostedAtDesc(pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().isPaged()).isTrue();
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
  }

  @Test
  void listTransactionsCapsLimitAtFiveHundred() {
    FinanceTransactionRepository repository = mock(FinanceTransactionRepository.class);
    when(repository.findAllByOrderByPostedAtDesc(any(Pageable.class))).thenReturn(List.of());
    DefaultFinanceInsightsService service = new DefaultFinanceInsightsService(repository, CLOCK);

    service.listTransactions(9999, null);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAllByOrderByPostedAtDesc(pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().isPaged()).isTrue();
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(500);
  }

  @Test
  void listTransactionsUsesCategoryRepositoryWhenCategoryPresent() {
    FinanceTransactionRepository repository = mock(FinanceTransactionRepository.class);
    when(repository.findByCategoryIgnoreCaseOrderByPostedAtDesc(any(), any(Pageable.class)))
        .thenReturn(List.<FinanceTransactionEntity>of());
    DefaultFinanceInsightsService service = new DefaultFinanceInsightsService(repository, CLOCK);

    service.listTransactions(20, "groceries");

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findByCategoryIgnoreCaseOrderByPostedAtDesc(any(), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
  }
}
