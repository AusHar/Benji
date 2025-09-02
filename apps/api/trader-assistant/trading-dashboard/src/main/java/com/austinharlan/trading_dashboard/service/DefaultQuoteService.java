package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.marketdata.MarketDataProvider;
import com.austinharlan.trading_dashboard.marketdata.Quote;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class DefaultQuoteService implements QuoteService {
  private final MarketDataProvider provider;

  public DefaultQuoteService(MarketDataProvider provider) {
    this.provider = provider;
  }

  @Override
  @Cacheable("quotes")
  public Quote getCached(String symbol) {
    return provider.getQuote(symbol);
  }
}
