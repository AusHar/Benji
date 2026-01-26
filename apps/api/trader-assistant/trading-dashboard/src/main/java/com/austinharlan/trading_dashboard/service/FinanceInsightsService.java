package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.finance.FinanceSummaryData;
import com.austinharlan.trading_dashboard.finance.FinanceTransactionRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FinanceInsightsService {
  FinanceSummaryData getSummary();

  List<FinanceTransactionRecord> listTransactions(Integer limit, String category);

  Optional<FinanceTransactionRecord> findById(String id);

  FinanceTransactionRecord create(
      Instant postedAt, String description, BigDecimal amount, String category, String notes);

  Optional<FinanceTransactionRecord> update(
      String id,
      Instant postedAt,
      String description,
      BigDecimal amount,
      String category,
      String notes);

  boolean delete(String id);
}
