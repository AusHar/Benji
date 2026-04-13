package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.finance.FinanceCategoryRecord;
import com.austinharlan.trading_dashboard.finance.FinanceSummaryData;
import com.austinharlan.trading_dashboard.finance.FinanceTransactionRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface FinanceInsightsService {
  FinanceSummaryData getSummary();

  List<FinanceTransactionRecord> listTransactions(Integer limit, String category);

  FinanceTransactionRecord createTransaction(
      Instant postedAt, String description, BigDecimal amount, String category, String notes);

  List<FinanceCategoryRecord> listCategories();

  FinanceCategoryRecord createCategory(String label);

  boolean deleteCategory(String slug);
}
