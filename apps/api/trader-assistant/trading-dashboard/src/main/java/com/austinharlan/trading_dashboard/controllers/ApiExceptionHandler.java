package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.marketdata.MarketDataRateLimitException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler(MarketDataRateLimitException.class)
  ResponseEntity<Map<String, String>> handleMarketDataRateLimitException(
      MarketDataRateLimitException ex) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .body(Map.of("error", "rate_limited", "message", ex.getMessage()));
  }
}
