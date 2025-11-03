package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.finance.FinanceSummaryData;
import com.austinharlan.trading_dashboard.finance.FinanceTransactionRecord;
import com.austinharlan.trading_dashboard.persistence.FinanceTransactionEntity;
import com.austinharlan.trading_dashboard.persistence.FinanceTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultFinanceInsightsService implements FinanceInsightsService {
  private final FinanceTransactionRepository transactionRepository;
  private final Clock clock;

  @Autowired
  public DefaultFinanceInsightsService(FinanceTransactionRepository transactionRepository) {
    this(transactionRepository, Clock.systemUTC());
  }

  public DefaultFinanceInsightsService(
      FinanceTransactionRepository transactionRepository, Clock clock) {
    this.transactionRepository = transactionRepository;
    this.clock = clock;
  }

  @Override
  public FinanceSummaryData getSummary() {
    Instant now = Instant.now(clock);
    YearMonth currentMonth = YearMonth.now(clock);
    LocalDate today = LocalDate.now(clock);
    Instant startOfMonth = currentMonth.atDay(1).atStartOfDay(clock.getZone()).toInstant();
    Instant startOfNextMonth =
        currentMonth.plusMonths(1).atDay(1).atStartOfDay(clock.getZone()).toInstant();

    List<FinanceTransactionRecord> monthTransactions =
        transactionRepository.findWithinRange(startOfMonth, startOfNextMonth).stream()
            .map(this::toRecord)
            .toList();

    BigDecimal monthToDate =
        monthTransactions.stream()
            .map(FinanceTransactionRecord::amount)
            .map(this::safeAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

    BigDecimal averageDaily =
        today.getDayOfMonth() > 0
            ? monthToDate.divide(BigDecimal.valueOf(today.getDayOfMonth()), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    BigDecimal projectedMonthEnd =
        averageDaily
            .multiply(BigDecimal.valueOf(currentMonth.lengthOfMonth()))
            .setScale(2, RoundingMode.HALF_UP);

    return new FinanceSummaryData(monthToDate, averageDaily, projectedMonthEnd, now);
  }

  @Override
  public List<FinanceTransactionRecord> listTransactions(Integer limit, String category) {
    Pageable pageable = resolvePageable(limit);
    if (StringUtils.hasText(category)) {
      String normalizedCategory = category.trim();
      return transactionRepository
          .findByCategoryIgnoreCaseOrderByPostedAtDesc(normalizedCategory, pageable)
          .stream()
          .map(this::toRecord)
          .toList();
    }

    return transactionRepository.findAllByOrderByPostedAtDesc(pageable).stream()
        .map(this::toRecord)
        .toList();
  }

  private BigDecimal safeAmount(BigDecimal amount) {
    return amount != null ? amount : BigDecimal.ZERO;
  }

  private Pageable resolvePageable(Integer limit) {
    if (limit == null || limit <= 0) {
      return Pageable.unpaged();
    }
    return PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "postedAt"));
  }

  private FinanceTransactionRecord toRecord(FinanceTransactionEntity entity) {
    return new FinanceTransactionRecord(
        entity.getId(),
        entity.getPostedAt(),
        entity.getDescription(),
        entity.getAmount(),
        entity.getCategory(),
        entity.getNotes());
  }
}
