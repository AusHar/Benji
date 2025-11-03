package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.finance.FinanceSummaryData;
import com.austinharlan.trading_dashboard.finance.FinanceTransactionRecord;
import java.util.List;

public interface FinanceInsightsService {
  FinanceSummaryData getSummary();

  List<FinanceTransactionRecord> listTransactions(Integer limit, String category);
}
