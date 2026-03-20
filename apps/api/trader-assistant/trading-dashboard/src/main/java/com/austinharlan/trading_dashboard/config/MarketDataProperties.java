package com.austinharlan.trading_dashboard.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "trading.marketdata")
@Validated
public class MarketDataProperties {
  @NotBlank private String query2BaseUrl;

  @NotBlank private String yahooRssBaseUrl;

  @NotBlank private String healthSymbol = "SPY";

  @NotNull private Duration healthCacheTtl = Duration.ofMinutes(1);

  @NotNull private Duration connectTimeout = Duration.ofSeconds(5);

  @NotNull private Duration readTimeout = Duration.ofSeconds(10);

  @NotNull private Duration writeTimeout = Duration.ofSeconds(10);

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
}
