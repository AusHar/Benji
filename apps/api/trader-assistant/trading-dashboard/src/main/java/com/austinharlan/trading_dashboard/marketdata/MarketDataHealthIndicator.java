package com.austinharlan.trading_dashboard.marketdata;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev")
public class MarketDataHealthIndicator implements HealthIndicator {
  private final MarketDataProperties properties;
  private final MarketDataProvider provider;
  private final Duration cacheTtl;
  private volatile Instant lastProbe = Instant.EPOCH;
  private volatile Health cachedHealth;

  public MarketDataHealthIndicator(MarketDataProperties properties, MarketDataProvider provider) {
    this.properties = properties;
    this.provider = provider;
    this.cacheTtl = properties.getHealthCacheTtl();
  }

  @Override
  public Health health() {
    if (cacheTtl.isZero() || cacheTtl.isNegative()) {
      return checkProvider();
    }

    Health cached = cachedHealth;
    Instant now = Instant.now();
    if (cached != null && Duration.between(lastProbe, now).compareTo(cacheTtl) < 0) {
      return cached;
    }

    synchronized (this) {
      cached = cachedHealth;
      now = Instant.now();
      if (cached != null && Duration.between(lastProbe, now).compareTo(cacheTtl) < 0) {
        return cached;
      }

      Health refreshed = checkProvider();
      cachedHealth = refreshed;
      lastProbe = now;
      return refreshed;
    }
  }

  private Health checkProvider() {
    try {
      Quote quote = provider.getQuote(properties.getHealthSymbol());
      return Health.up()
          .withDetail("symbol", quote.symbol())
          .withDetail("price", quote.price())
          .withDetail("timestamp", quote.timestamp())
          .build();
    } catch (Exception ex) {
      return Health.down(ex).build();
    }
  }
}
