package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.finance.FinanceSummaryData;
import com.austinharlan.trading_dashboard.finance.FinanceTransactionRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DefaultFinanceInsightsService implements FinanceInsightsService {
  private final Clock clock;

  public DefaultFinanceInsightsService() {
    this(Clock.systemUTC());
  }

  @Override
  public FinanceSummaryData getSummary() {
    Instant now = Instant.now(clock);
    YearMonth currentMonth = YearMonth.now(clock);
    LocalDate today = LocalDate.now(clock);

    BigDecimal monthToDate =
        transactionsStream()
            .filter(record -> isInMonth(record.postedAt(), currentMonth))
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
    Stream<FinanceTransactionRecord> stream = transactionsStream();
    if (StringUtils.hasText(category)) {
      String normalizedCategory = category.trim().toLowerCase(Locale.US);
      stream =
          stream.filter(
              record ->
                  record.category() != null
                      && record.category().toLowerCase(Locale.US).equals(normalizedCategory));
    }

    List<FinanceTransactionRecord> ordered =
        stream.sorted(Comparator.comparing(FinanceTransactionRecord::postedAt).reversed()).toList();

    if (limit != null && limit > 0 && ordered.size() > limit) {
      return ordered.subList(0, limit);
    }

    return ordered;
  }

  private Stream<FinanceTransactionRecord> transactionsStream() {
    return Stream.<FinanceTransactionRecord>of();
  }

  private boolean isInMonth(Instant postedAt, YearMonth month) {
    if (postedAt == null) {
      return false;
    }
    return YearMonth.from(postedAt.atZone(clock.getZone())).equals(month);
  }

  private BigDecimal safeAmount(BigDecimal amount) {
    return amount != null ? amount : BigDecimal.ZERO;
  }
}
