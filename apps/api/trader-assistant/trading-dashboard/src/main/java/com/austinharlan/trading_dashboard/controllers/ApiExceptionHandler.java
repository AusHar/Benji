package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.marketdata.MarketDataClientException;
import com.austinharlan.trading_dashboard.marketdata.MarketDataRateLimitException;
import com.austinharlan.trading_dashboard.marketdata.QuoteNotFoundException;
import com.austinharlan.tradingdashboard.dto.ErrorResponse;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler(MarketDataRateLimitException.class)
  ResponseEntity<ErrorResponse> handleMarketDataRateLimitException(
      MarketDataRateLimitException ex) {
    return build(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", ex.getMessage());
  }

  @ExceptionHandler(InvalidTickerException.class)
  ResponseEntity<ErrorResponse> handleInvalidTicker(InvalidTickerException ex) {
    return build(HttpStatus.BAD_REQUEST, "SYMBOL_INVALID", ex.getMessage());
  }

  @ExceptionHandler(QuoteNotFoundException.class)
  ResponseEntity<ErrorResponse> handleQuoteNotFound(QuoteNotFoundException ex) {
    return build(HttpStatus.NOT_FOUND, "QUOTE_NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(MarketDataClientException.class)
  ResponseEntity<ErrorResponse> handleMarketDataClientException(MarketDataClientException ex) {
    return build(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR", ex.getMessage());
  }

  private ResponseEntity<ErrorResponse> build(
      HttpStatus status, String code, String message, String... details) {
    ErrorResponse body = new ErrorResponse().code(code).message(message);
    if (details != null && details.length > 0) {
      body.setDetails(Arrays.asList(details));
    }
    return ResponseEntity.status(status).body(body);
  }
}
