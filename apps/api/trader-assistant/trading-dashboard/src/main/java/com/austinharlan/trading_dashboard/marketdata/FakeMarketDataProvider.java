package com.austinharlan.trading_dashboard.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class FakeMarketDataProvider implements MarketDataProvider {
  @Override
  public Quote getQuote(String symbol) {
    return new Quote(symbol, BigDecimal.valueOf(100.00), Instant.now());
  }
}
