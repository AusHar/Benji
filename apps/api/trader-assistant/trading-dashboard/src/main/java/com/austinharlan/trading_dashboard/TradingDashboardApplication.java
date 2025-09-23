package com.austinharlan.trading_dashboard;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.austinharlan") // ensures all subpackages are scanned
@EnableConfigurationProperties(MarketDataProperties.class)
public class TradingDashboardApplication {
  public static void main(String[] args) {
    SpringApplication.run(TradingDashboardApplication.class, args);
  }
}
