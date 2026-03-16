package com.austinharlan.trading_dashboard.marketdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class FakeMarketDataProvider implements MarketDataProvider {
  @Override
  public Quote getQuote(String symbol) {
    return new Quote(symbol, BigDecimal.valueOf(100.00), BigDecimal.valueOf(1.23), Instant.now());
  }

  @Override
  public CompanyOverview getOverview(String symbol) {
    return new CompanyOverview(
        symbol,
        symbol + " Inc.",
        "Technology",
        "Software",
        BigDecimal.valueOf(3_000_000_000_000L),
        BigDecimal.valueOf(25.40),
        BigDecimal.valueOf(6.50),
        BigDecimal.valueOf(0.0055),
        BigDecimal.valueOf(1.18),
        BigDecimal.valueOf(120.00),
        BigDecimal.valueOf(78.50));
  }

  @Override
  public List<DailyBar> getDailyHistory(String symbol) {
    Random rng = new Random(symbol.hashCode());
    List<DailyBar> bars = new ArrayList<>();
    double price = 95.0;
    LocalDate date = LocalDate.now().minusDays(100);
    for (int i = 0; i < 100; i++) {
      price += (rng.nextDouble() - 0.48) * 2.0;
      if (price < 20) price = 20;
      double open = price + (rng.nextDouble() - 0.5);
      double high = Math.max(open, price) + rng.nextDouble() * 2;
      double low = Math.min(open, price) - rng.nextDouble() * 2;
      long volume = 10_000_000L + rng.nextInt(50_000_000);
      bars.add(new DailyBar(date, bd(open), bd(high), bd(low), bd(price), volume));
      date = date.plusDays(1);
    }
    return bars;
  }

  private static BigDecimal bd(double v) {
    return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
  }
}
