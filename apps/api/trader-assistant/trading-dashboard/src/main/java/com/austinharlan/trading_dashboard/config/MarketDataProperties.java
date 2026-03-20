package com.austinharlan.trading_dashboard.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "trading.marketdata")
@Validated
public class MarketDataProperties {
  @NotBlank private String baseUrl;

  @NotBlank private String apiKey;

  @NotBlank private String healthSymbol = "SPY";

  @NotNull private Duration healthCacheTtl = Duration.ofMinutes(1);

  @NotNull private Duration connectTimeout = Duration.ofSeconds(2);

  @NotNull private Duration readTimeout = Duration.ofSeconds(5);

  @NotNull private Duration writeTimeout = Duration.ofSeconds(5);

  private RetryProperties retry = new RetryProperties();

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getHealthSymbol() {
    return healthSymbol;
  }

  public void setHealthSymbol(String healthSymbol) {
    this.healthSymbol =
        (healthSymbol != null && !healthSymbol.isBlank()) ? healthSymbol : this.healthSymbol;
  }

  public Duration getHealthCacheTtl() {
    return healthCacheTtl;
  }

  public void setHealthCacheTtl(Duration healthCacheTtl) {
    this.healthCacheTtl = healthCacheTtl != null ? healthCacheTtl : Duration.ofMinutes(1);
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(2);
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(5);
  }

  public Duration getWriteTimeout() {
    return writeTimeout;
  }

  public void setWriteTimeout(Duration writeTimeout) {
    this.writeTimeout = writeTimeout != null ? writeTimeout : Duration.ofSeconds(5);
  }

  public RetryProperties getRetry() {
    return retry;
  }

  public void setRetry(RetryProperties retry) {
    this.retry = retry != null ? retry : new RetryProperties();
  }

  public static class RetryProperties {
    @Positive private int maxAttempts = 3;

    @NotNull private Duration initialBackoff = Duration.ofMillis(500);

    @NotNull private Duration maxBackoff = Duration.ofSeconds(5);

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts > 0 ? maxAttempts : 3;
    }

    public Duration getInitialBackoff() {
      return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {
      this.initialBackoff = initialBackoff != null ? initialBackoff : Duration.ofMillis(500);
    }

    public Duration getMaxBackoff() {
      return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
      this.maxBackoff = maxBackoff != null ? maxBackoff : Duration.ofSeconds(5);
    }
  }
}
