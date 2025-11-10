package com.austinharlan.trading_dashboard.controllers;

final class InvalidTickerException extends RuntimeException {

  InvalidTickerException(String message) {
    super(message);
  }
}
