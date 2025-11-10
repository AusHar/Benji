package com.austinharlan.trading_dashboard.marketdata;

public class QuoteNotFoundException extends RuntimeException {

  public QuoteNotFoundException(String message) {
    super(message);
  }
}
