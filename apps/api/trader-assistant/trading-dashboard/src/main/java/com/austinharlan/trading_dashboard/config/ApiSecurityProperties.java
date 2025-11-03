package com.austinharlan.trading_dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "trading.api")
public class ApiSecurityProperties {

  private String key;
  private String headerName = "X-API-KEY";

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getHeaderName() {
    return headerName;
  }

  public void setHeaderName(String headerName) {
    if (StringUtils.hasText(headerName)) {
      this.headerName = headerName;
    }
  }

  public boolean isEnabled() {
    return StringUtils.hasText(key);
  }
}
