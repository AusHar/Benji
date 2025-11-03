package com.austinharlan.trader.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading.cache")
public class CacheProperties {

  private final Quotes quotes = new Quotes();

  public Quotes getQuotes() {
    return quotes;
  }

  public static class Quotes {
    private Duration ttl = Duration.ofSeconds(30);
    private long maximumSize = 1024;

    public Duration getTtl() {
      return ttl;
    }

    public void setTtl(Duration ttl) {
      this.ttl = ttl == null ? Duration.ofSeconds(30) : ttl;
    }

    public long getMaximumSize() {
      return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
      this.maximumSize = maximumSize > 0 ? maximumSize : 1024;
    }
  }
}
