package com.austinharlan.trading_dashboard.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

  @Bean
  @ConfigurationProperties(prefix = "trading.rate-limit")
  public RateLimitProperties rateLimitProperties() {
    return new RateLimitProperties();
  }

  @Bean
  public RateLimitService rateLimitService(RateLimitProperties properties) {
    return new RateLimitService(properties);
  }

  public static class RateLimitProperties {
    private boolean enabled = true;
    private int requestsPerMinute = 100;
    private int quoteRequestsPerMinute = 30;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getRequestsPerMinute() {
      return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
      this.requestsPerMinute = requestsPerMinute;
    }

    public int getQuoteRequestsPerMinute() {
      return quoteRequestsPerMinute;
    }

    public void setQuoteRequestsPerMinute(int quoteRequestsPerMinute) {
      this.quoteRequestsPerMinute = quoteRequestsPerMinute;
    }
  }

  public static class RateLimitService {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;

    public RateLimitService(RateLimitProperties properties) {
      this.properties = properties;
    }

    public boolean isEnabled() {
      return properties.isEnabled();
    }

    public Bucket resolveBucket(String key, boolean isQuoteEndpoint) {
      return buckets.computeIfAbsent(
          key,
          k -> {
            int limit =
                isQuoteEndpoint
                    ? properties.getQuoteRequestsPerMinute()
                    : properties.getRequestsPerMinute();
            Bandwidth bandwidth =
                Bandwidth.classic(limit, Refill.greedy(limit, Duration.ofMinutes(1)));
            return Bucket.builder().addLimit(bandwidth).build();
          });
    }

    public long getWaitTimeSeconds(Bucket bucket) {
      return bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill() / 1_000_000_000;
    }
  }
}
