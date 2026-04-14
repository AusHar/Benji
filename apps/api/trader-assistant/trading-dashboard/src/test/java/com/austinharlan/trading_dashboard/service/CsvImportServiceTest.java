package com.austinharlan.trading_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CsvImportServiceTest {

  // ── parseAmount ───────────────────────────────────────────────────────────

  @Test
  void parseAmount_positive() {
    assertThat(CsvImportService.parseAmount("$1,799.92"))
        .isEqualByComparingTo(new BigDecimal("1799.92"));
  }

  @Test
  void parseAmount_negative_parentheses() {
    assertThat(CsvImportService.parseAmount("($520.08)"))
        .isEqualByComparingTo(new BigDecimal("-520.08"));
  }

  @Test
  void parseAmount_empty_returns_null() {
    assertThat(CsvImportService.parseAmount("")).isNull();
    assertThat(CsvImportService.parseAmount(null)).isNull();
  }

  @Test
  void parseAmount_small_positive() {
    assertThat(CsvImportService.parseAmount("$0.01")).isEqualByComparingTo(new BigDecimal("0.01"));
  }

  @Test
  void parseAmount_negative_large_with_comma() {
    assertThat(CsvImportService.parseAmount("($1,000.00)"))
        .isEqualByComparingTo(new BigDecimal("-1000.00"));
  }

  // ── parseOptionDescription ────────────────────────────────────────────────

  @Test
  void parseOptionDescription_standard_call() {
    var result = CsvImportService.parseOptionDescription("AAPL 3/9/2026 Call $257.50");
    assertThat(result).isNotNull();
    assertThat(result.optionType()).isEqualTo("CALL");
    assertThat(result.strikePrice()).isEqualByComparingTo(new BigDecimal("257.50"));
    assertThat(result.expirationDate()).isEqualTo(LocalDate.of(2026, 3, 9));
  }

  @Test
  void parseOptionDescription_standard_put() {
    var result = CsvImportService.parseOptionDescription("RXRX 4/2/2026 Put $3.50");
    assertThat(result).isNotNull();
    assertThat(result.optionType()).isEqualTo("PUT");
    assertThat(result.strikePrice()).isEqualByComparingTo(new BigDecimal("3.50"));
    assertThat(result.expirationDate()).isEqualTo(LocalDate.of(2026, 4, 2));
  }

  @Test
  void parseOptionDescription_expiration() {
    var result =
        CsvImportService.parseOptionDescription("Option Expiration for RXRX 3/27/2026 Call $3.50");
    assertThat(result).isNotNull();
    assertThat(result.optionType()).isEqualTo("CALL");
    assertThat(result.strikePrice()).isEqualByComparingTo(new BigDecimal("3.50"));
    assertThat(result.expirationDate()).isEqualTo(LocalDate.of(2026, 3, 27));
  }

  @Test
  void parseOptionDescription_oasgn() {
    var result = CsvImportService.parseOptionDescription("RXRX 1/16/2026 Call $4.50");
    assertThat(result).isNotNull();
    assertThat(result.optionType()).isEqualTo("CALL");
    assertThat(result.strikePrice()).isEqualByComparingTo(new BigDecimal("4.50"));
    assertThat(result.expirationDate()).isEqualTo(LocalDate.of(2026, 1, 16));
  }

  @Test
  void parseOptionDescription_equity_description_returns_null() {
    assertThat(CsvImportService.parseOptionDescription("Apple\nCUSIP: 037833100")).isNull();
    assertThat(CsvImportService.parseOptionDescription("Dolby\nCUSIP: 25659T107")).isNull();
  }

  @Test
  void parseOptionDescription_null_returns_null() {
    assertThat(CsvImportService.parseOptionDescription(null)).isNull();
  }
}
