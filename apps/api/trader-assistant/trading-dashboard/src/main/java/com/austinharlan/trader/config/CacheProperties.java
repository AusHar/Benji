package com.austinharlan.trader.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading.cache")
public class CacheProperties {

  private final Quotes quotes = new Quotes();
  private final Overview overview = new Overview();
  private final History history = new History();
  private final News news = new News();
  private final DailyImage dailyImage = new DailyImage();

  public Quotes getQuotes() {
    return quotes;
  }

  public Overview getOverview() {
    return overview;
  }

  public History getHistory() {
    return history;
  }

  public News getNews() {
    return news;
  }

  public DailyImage getDailyImage() {
    return dailyImage;
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

  public static class Overview {
    private Duration ttl = Duration.ofHours(4);
    private long maximumSize = 256;

    public Duration getTtl() {
      return ttl;
    }

    public void setTtl(Duration ttl) {
      this.ttl = ttl == null ? Duration.ofHours(4) : ttl;
    }

    public long getMaximumSize() {
      return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
      this.maximumSize = maximumSize > 0 ? maximumSize : 256;
    }
  }

  public static class History {
    private Duration ttl = Duration.ofHours(1);
    private long maximumSize = 256;

    public Duration getTtl() {
      return ttl;
    }

    public void setTtl(Duration ttl) {
      this.ttl = ttl == null ? Duration.ofHours(1) : ttl;
    }

    public long getMaximumSize() {
      return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
      this.maximumSize = maximumSize > 0 ? maximumSize : 256;
    }
  }

  public static class News {
    private Duration ttl = Duration.ofMinutes(15);
    private long maximumSize = 256;

    public Duration getTtl() {
      return ttl;
    }

    public void setTtl(Duration ttl) {
      this.ttl = ttl == null ? Duration.ofMinutes(15) : ttl;
    }

    public long getMaximumSize() {
      return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
      this.maximumSize = maximumSize > 0 ? maximumSize : 256;
    }
  }

  public static class DailyImage {
    private Duration ttl = Duration.ofHours(24);
    private long maximumSize = 1;

    public Duration getTtl() {
      return ttl;
    }

    public void setTtl(Duration ttl) {
      this.ttl = ttl == null ? Duration.ofHours(24) : ttl;
    }

    public long getMaximumSize() {
      return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
      this.maximumSize = maximumSize > 0 ? maximumSize : 1;
    }
  }
}
