package com.austinharlan.trading_dashboard.marketdata;

import java.util.List;

public interface MarketDataProvider {
  Quote getQuote(String symbol);

  default CompanyOverview getOverview(String symbol) {
    throw new UnsupportedOperationException("getOverview not implemented");
  }

  default List<DailyBar> getDailyHistory(String symbol) {
    throw new UnsupportedOperationException("getDailyHistory not implemented");
  }
}
