package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.persistence.FinanceTransactionRepository;
import com.austinharlan.trading_dashboard.persistence.TradeRepository;
import com.austinharlan.tradingdashboard.dto.ImportConfirmResponse;
import com.austinharlan.tradingdashboard.dto.ImportPreviewResponse;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class CsvImportService implements ImportService {

  // ── Regex patterns ────────────────────────────────────────────────────────

  // Matches: "AAPL 3/9/2026 Call $257.50" or "RXRX 4/2/2026 Put $3.50"
  static final Pattern OPTION_DESC =
      Pattern.compile("^\\S+\\s+(\\d{1,2}/\\d{1,2}/\\d{4})\\s+(Call|Put)\\s+\\$([\\d,.]+)$");

  // Matches: "Option Expiration for RXRX 3/27/2026 Call $3.50"
  static final Pattern OEXP_DESC =
      Pattern.compile(
          "^Option Expiration for \\S+\\s+(\\d{1,2}/\\d{1,2}/\\d{4})\\s+(Call|Put)\\s+\\$([\\d,.]+)$");

  static final DateTimeFormatter RH_DATE = DateTimeFormatter.ofPattern("M/d/yyyy");

  private final TradeRepository tradeRepository;
  private final FinanceTransactionRepository financeTransactionRepository;

  public CsvImportService(
      TradeRepository tradeRepository, FinanceTransactionRepository financeTransactionRepository) {
    this.tradeRepository = tradeRepository;
    this.financeTransactionRepository = financeTransactionRepository;
  }

  // ── Amount parsing ────────────────────────────────────────────────────────

  /**
   * Parses Robinhood accounting-notation amounts.
   *
   * <p>"$1,799.92" → 1799.92, "($520.08)" → -520.08, "" or null → null
   */
  @Nullable
  static BigDecimal parseAmount(@Nullable String raw) {
    if (raw == null || raw.isBlank()) return null;
    boolean negative = raw.startsWith("(") && raw.endsWith(")");
    String cleaned = raw.replaceAll("[()$,\\s]", "");
    if (cleaned.isEmpty()) return null;
    BigDecimal value = new BigDecimal(cleaned);
    return negative ? value.negate() : value;
  }

  // ── Option description parsing ────────────────────────────────────────────

  record OptionDetails(String optionType, BigDecimal strikePrice, LocalDate expirationDate) {}

  /**
   * Parses option contract details from a Robinhood description string.
   *
   * <p>Returns null if the description does not match a known option pattern (caller should treat
   * as a parse error for OPTION trans codes).
   */
  @Nullable
  static OptionDetails parseOptionDescription(String description) {
    if (description == null) return null;
    // Try OEXP pattern first (more specific)
    Matcher m = OEXP_DESC.matcher(description.trim());
    if (!m.matches()) {
      m = OPTION_DESC.matcher(description.trim());
    }
    if (!m.matches()) return null;
    LocalDate expiry = LocalDate.parse(m.group(1), RH_DATE);
    String optionType = m.group(2).toUpperCase();
    BigDecimal strike = new BigDecimal(m.group(3).replace(",", ""));
    return new OptionDetails(optionType, strike, expiry);
  }

  // ── SHA-256 helper ────────────────────────────────────────────────────────

  static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  // ── ImportService methods (stubs — implemented in Task 7) ─────────────────

  @Override
  public ImportPreviewResponse preview(MultipartFile file, String account) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public ImportConfirmResponse confirm(MultipartFile file, String account) {
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
