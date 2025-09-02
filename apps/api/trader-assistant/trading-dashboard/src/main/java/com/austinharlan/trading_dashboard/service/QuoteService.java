package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.marketdata.Quote;

public interface QuoteService {
  Quote getCached(String symbol);
}
