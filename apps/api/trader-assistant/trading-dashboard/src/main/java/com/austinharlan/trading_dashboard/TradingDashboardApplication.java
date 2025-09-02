package com.austinharlan.trading_dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.austinharlan") // ensures all subpackages are scanned
public class TradingDashboardApplication {
  public static void main(String[] args) {
    SpringApplication.run(TradingDashboardApplication.class, args);
  }
}
