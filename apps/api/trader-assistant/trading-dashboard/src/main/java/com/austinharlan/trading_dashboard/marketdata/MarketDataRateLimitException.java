package com.austinharlan.trading_dashboard.marketdata;

public class MarketDataRateLimitException extends MarketDataClientException {

  public MarketDataRateLimitException(String message) {
    super(message);
  }

  public MarketDataRateLimitException(String message, Throwable cause) {
    super(message, cause);
  }
}
