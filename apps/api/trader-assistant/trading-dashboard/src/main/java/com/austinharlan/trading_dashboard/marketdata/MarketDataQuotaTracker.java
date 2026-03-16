package com.austinharlan.trading_dashboard.marketdata;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Tracks the number of Finnhub API calls made in the current one-minute window. Thread-safe,
 * resets whenever a full minute has elapsed since the window started.
 */
@Component
@Profile("!dev")
public class MarketDataQuotaTracker {

  public static final int CALLS_PER_MINUTE_LIMIT = 60;

  /** Alias kept for callers that reference the old constant name. */
  public static final int DAILY_LIMIT = CALLS_PER_MINUTE_LIMIT;

  private final AtomicInteger count = new AtomicInteger(0);
  private volatile Instant windowStart = Instant.now();

  public void increment() {
    resetIfNewWindow();
    count.incrementAndGet();
  }

  public int getUsed() {
    resetIfNewWindow();
    return count.get();
  }

  public int getDailyLimit() {
    return CALLS_PER_MINUTE_LIMIT;
  }

  private void resetIfNewWindow() {
    Instant now = Instant.now();
    if (Duration.between(windowStart, now).toSeconds() >= 60) {
      synchronized (this) {
        if (Duration.between(windowStart, now).toSeconds() >= 60) {
          windowStart = now;
          count.set(0);
        }
      }
    }
  }
}
