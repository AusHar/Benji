package com.austinharlan.trading_dashboard.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "trading.marketdata")
@Validated
public class MarketDataProperties {
  @NotBlank private String baseUrl;

  @NotBlank private String query2BaseUrl;

  @NotBlank private String yahooRssBaseUrl;

  @NotBlank private String coinGeckoBaseUrl;

  private Map<String, String> cryptoSymbols = new HashMap<>();

  @NotBlank private String healthSymbol = "SPY";

  @NotNull private Duration healthCacheTtl = Duration.ofMinutes(1);

  @NotNull private Duration connectTimeout = Duration.ofSeconds(5);

  @NotNull private Duration readTimeout = Duration.ofSeconds(10);

  @NotNull private Duration writeTimeout = Duration.ofSeconds(10);

  private RetryProperties retry = new RetryProperties();

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getQuery2BaseUrl() {
    return query2BaseUrl;
  }

  public void setQuery2BaseUrl(String query2BaseUrl) {
    this.query2BaseUrl = query2BaseUrl;
  }

  public String getYahooRssBaseUrl() {
    return yahooRssBaseUrl;
  }

  public void setYahooRssBaseUrl(String yahooRssBaseUrl) {
    this.yahooRssBaseUrl = yahooRssBaseUrl;
  }

  public String getCoinGeckoBaseUrl() {
    return coinGeckoBaseUrl;
  }

  public void setCoinGeckoBaseUrl(String coinGeckoBaseUrl) {
    this.coinGeckoBaseUrl = coinGeckoBaseUrl;
  }

  public Map<String, String> getCryptoSymbols() {
    return cryptoSymbols;
  }

  public void setCryptoSymbols(Map<String, String> cryptoSymbols) {
    this.cryptoSymbols = cryptoSymbols != null ? cryptoSymbols : new HashMap<>();
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
    this.connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(5);
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(10);
  }

  public Duration getWriteTimeout() {
    return writeTimeout;
  }

  public void setWriteTimeout(Duration writeTimeout) {
    this.writeTimeout = writeTimeout != null ? writeTimeout : Duration.ofSeconds(10);
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
