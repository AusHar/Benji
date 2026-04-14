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

  // ── tradeSideFor ──────────────────────────────────────────────────────────

  @Test
  void tradeSideFor_equity_buy() {
    assertThat(CsvImportService.tradeSideFor("Buy")).isEqualTo("BUY");
  }

  @Test
  void tradeSideFor_equity_sell() {
    assertThat(CsvImportService.tradeSideFor("Sell")).isEqualTo("SELL");
  }

  @Test
  void tradeSideFor_bto() {
    assertThat(CsvImportService.tradeSideFor("BTO")).isEqualTo("BUY");
  }

  @Test
  void tradeSideFor_stc() {
    assertThat(CsvImportService.tradeSideFor("STC")).isEqualTo("SELL");
  }

  @Test
  void tradeSideFor_sto() {
    assertThat(CsvImportService.tradeSideFor("STO")).isEqualTo("SELL");
  }

  @Test
  void tradeSideFor_btc() {
    assertThat(CsvImportService.tradeSideFor("BTC")).isEqualTo("BUY");
  }

  @Test
  void tradeSideFor_oexp() {
    assertThat(CsvImportService.tradeSideFor("OEXP")).isEqualTo("EXPIRE");
  }

  @Test
  void tradeSideFor_oasgn() {
    assertThat(CsvImportService.tradeSideFor("OASGN")).isEqualTo("EXERCISE");
  }

  @Test
  void tradeSideFor_ach_returns_null() {
    assertThat(CsvImportService.tradeSideFor("ACH")).isNull();
  }

  @Test
  void tradeSideFor_futswp_returns_null() {
    assertThat(CsvImportService.tradeSideFor("FUTSWP")).isNull();
  }

  @Test
  void isCashEvent_cdiv() {
    assertThat(CsvImportService.isCashEvent("CDIV")).isTrue();
  }

  @Test
  void isCashEvent_slip() {
    assertThat(CsvImportService.isCashEvent("SLIP")).isTrue();
  }

  @Test
  void isCashEvent_int() {
    assertThat(CsvImportService.isCashEvent("INT")).isTrue();
  }

  @Test
  void isCashEvent_buy_returns_false() {
    assertThat(CsvImportService.isCashEvent("Buy")).isFalse();
  }

  // ── computeDedupKeys ──────────────────────────────────────────────────────

  @Test
  void computeDedupKeys_identical_rows_get_distinct_keys() {
    var row1 =
        new CsvImportService.RawRow(
            2,
            "3/9/2026",
            "3/9/2026",
            "3/10/2026",
            "AAPL",
            "AAPL 3/9/2026 Call $257.50",
            "BTO",
            "1",
            "$0.35",
            "($35.04)");
    var row2 =
        new CsvImportService.RawRow(
            3,
            "3/9/2026",
            "3/9/2026",
            "3/10/2026",
            "AAPL",
            "AAPL 3/9/2026 Call $257.50",
            "BTO",
            "1",
            "$0.35",
            "($35.04)");
    var keys = CsvImportService.computeDedupKeys("INDIVIDUAL", java.util.List.of(row1, row2));
    assertThat(keys).hasSize(2);
    assertThat(keys.get(0)).isNotEqualTo(keys.get(1));
  }

  @Test
  void computeDedupKeys_same_csv_reimport_produces_same_keys() {
    var row =
        new CsvImportService.RawRow(
            2,
            "3/9/2026",
            "3/9/2026",
            "3/10/2026",
            "AAPL",
            "AAPL 3/9/2026 Call $257.50",
            "BTO",
            "1",
            "$0.35",
            "($35.04)");
    var keys1 = CsvImportService.computeDedupKeys("INDIVIDUAL", java.util.List.of(row));
    var keys2 = CsvImportService.computeDedupKeys("INDIVIDUAL", java.util.List.of(row));
    assertThat(keys1).isEqualTo(keys2);
  }

  @Test
  void computeDedupKeys_different_accounts_produce_different_keys() {
    var row =
        new CsvImportService.RawRow(
            2,
            "3/9/2026",
            "3/9/2026",
            "3/10/2026",
            "AAPL",
            "AAPL 3/9/2026 Call $257.50",
            "BTO",
            "1",
            "$0.35",
            "($35.04)");
    var keysIndividual = CsvImportService.computeDedupKeys("INDIVIDUAL", java.util.List.of(row));
    var keysRoth = CsvImportService.computeDedupKeys("ROTH_IRA", java.util.List.of(row));
    assertThat(keysIndividual.get(0)).isNotEqualTo(keysRoth.get(0));
  }

  // ── Pipeline helpers ──────────────────────────────────────────────────────

  @Test
  void isOptionTransCode_buy_returns_false() {
    assertThat(CsvImportService.isOptionTransCode("Buy")).isFalse();
  }

  @Test
  void isOptionTransCode_bto_returns_true() {
    assertThat(CsvImportService.isOptionTransCode("BTO")).isTrue();
  }

  @Test
  void isOptionTransCode_oexp_returns_true() {
    assertThat(CsvImportService.isOptionTransCode("OEXP")).isTrue();
  }

  @Test
  void isOptionTransCode_oasgn_returns_true() {
    assertThat(CsvImportService.isOptionTransCode("OASGN")).isTrue();
  }
}
