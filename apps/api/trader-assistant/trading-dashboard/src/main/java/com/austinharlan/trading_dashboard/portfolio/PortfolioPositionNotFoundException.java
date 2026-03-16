package com.austinharlan.trading_dashboard.portfolio;

public class PortfolioPositionNotFoundException extends RuntimeException {
  public PortfolioPositionNotFoundException(String ticker) {
    super("No position found for ticker: " + ticker);
  }
}
