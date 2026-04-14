package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.marketdata.MarketDataClientException;
import com.austinharlan.trading_dashboard.marketdata.QuoteNotFoundException;
import com.austinharlan.trading_dashboard.portfolio.PortfolioPositionNotFoundException;
import com.austinharlan.tradingdashboard.dto.ErrorResponse;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(InvalidTickerException.class)
  ResponseEntity<ErrorResponse> handleInvalidTicker(InvalidTickerException ex) {
    return build(HttpStatus.BAD_REQUEST, "SYMBOL_INVALID", ex.getMessage());
  }

  @ExceptionHandler(QuoteNotFoundException.class)
  ResponseEntity<ErrorResponse> handleQuoteNotFound(QuoteNotFoundException ex) {
    return build(HttpStatus.NOT_FOUND, "QUOTE_NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(PortfolioPositionNotFoundException.class)
  ResponseEntity<ErrorResponse> handlePortfolioPositionNotFound(
      PortfolioPositionNotFoundException ex) {
    return build(HttpStatus.NOT_FOUND, "POSITION_NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
  ResponseEntity<ErrorResponse> handleEntityNotFound(
      jakarta.persistence.EntityNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse().code("NOT_FOUND").message(ex.getMessage()));
  }

  @ExceptionHandler(MarketDataClientException.class)
  ResponseEntity<ErrorResponse> handleMarketDataClientException(MarketDataClientException ex) {
    return build(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR", ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<String> fieldErrors =
        ex.getBindingResult().getAllErrors().stream()
            .map(
                err -> {
                  if (err instanceof FieldError fe) {
                    return fe.getField() + ": " + fe.getDefaultMessage();
                  }
                  return err.getDefaultMessage();
                })
            .toList();
    String summary =
        fieldErrors.isEmpty() ? "Request validation failed." : String.join("; ", fieldErrors);
    return build(
        HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", summary, fieldErrors.toArray(new String[0]));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
    return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
    return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
    return build(
        HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or not valid JSON.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
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
