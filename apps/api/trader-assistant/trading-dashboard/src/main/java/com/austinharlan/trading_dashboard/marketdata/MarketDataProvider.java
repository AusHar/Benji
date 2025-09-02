package com.austinharlan.trading_dashboard.marketdata;

public interface MarketDataProvider {
  Quote getQuote(String symbol);
}
