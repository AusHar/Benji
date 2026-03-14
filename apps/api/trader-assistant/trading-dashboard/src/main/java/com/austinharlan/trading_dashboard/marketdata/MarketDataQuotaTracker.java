package com.austinharlan.trading_dashboard.marketdata;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Tracks the number of real AlphaVantage API calls made today. Thread-safe, resets at UTC midnight.
 */
@Component
@Profile("!dev")
public class MarketDataQuotaTracker {

  public static final int DAILY_LIMIT = 25;

  private final AtomicInteger count = new AtomicInteger(0);
  private volatile LocalDate currentDay = LocalDate.now(ZoneOffset.UTC);

  public void increment() {
    resetIfNewDay();
    count.incrementAndGet();
  }

  public int getUsed() {
    resetIfNewDay();
    return count.get();
  }

  public int getDailyLimit() {
    return DAILY_LIMIT;
  }

  private void resetIfNewDay() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    if (!today.equals(currentDay)) {
      currentDay = today;
      count.set(0);
    }
  }
}
