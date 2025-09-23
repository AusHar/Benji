package com.austinharlan.trading_dashboard.marketdata;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev")
public class MarketDataHealthIndicator implements HealthIndicator {
  private final MarketDataProperties properties;
  private final MarketDataProvider provider;

  public MarketDataHealthIndicator(MarketDataProperties properties, MarketDataProvider provider) {
    this.properties = properties;
    this.provider = provider;
  }

  @Override
  public Health health() {
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
