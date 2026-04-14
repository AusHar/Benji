package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.config.UserContext;
import com.austinharlan.trading_dashboard.persistence.FinanceTransactionEntity;
import com.austinharlan.trading_dashboard.persistence.FinanceTransactionRepository;
import com.austinharlan.trading_dashboard.persistence.TradeEntity;
import com.austinharlan.trading_dashboard.persistence.TradeRepository;
import com.austinharlan.tradingdashboard.dto.ImportConfirmResponse;
import com.austinharlan.tradingdashboard.dto.ImportPreviewResponse;
import com.austinharlan.tradingdashboard.dto.ImportPreviewSummary;
import com.austinharlan.tradingdashboard.dto.ImportRowResult;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private final PortfolioService portfolioService;

  public CsvImportService(
      TradeRepository tradeRepository,
      FinanceTransactionRepository financeTransactionRepository,
      PortfolioService portfolioService) {
    this.tradeRepository = tradeRepository;
    this.financeTransactionRepository = financeTransactionRepository;
    this.portfolioService = portfolioService;
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

  // ── Inner types ───────────────────────────────────────────────────────────

  enum RowAction {
    IMPORT_TRADE,
    IMPORT_CASH_EVENT,
    SKIP_DUPLICATE,
    SKIP_UNSUPPORTED,
    ERROR
  }

  record RawRow(
      int rowNumber,
      String activityDate,
      String processDate,
      String settleDate,
      String instrument,
      String description,
      String transCode,
      String rawQuantity,
      String rawPrice,
      String rawAmount) {}

  // ── Trans code classification ─────────────────────────────────────────────

  /** Returns TRADE_SIDE for trade trans codes, null for non-trade trans codes. */
  @Nullable
  static String tradeSideFor(String transCode) {
    return switch (transCode) {
      case "Buy", "BTO", "BTC" -> "BUY";
      case "Sell", "STC", "STO" -> "SELL";
      case "OEXP" -> "EXPIRE";
      case "OASGN" -> "EXERCISE";
      default -> null;
    };
  }

  static boolean isCashEvent(String transCode) {
    return switch (transCode) {
      case "CDIV", "SLIP", "INT" -> true;
      default -> false;
    };
  }

  static boolean isOptionTransCode(String transCode) {
    return switch (transCode) {
      case "BTO", "STC", "STO", "BTC", "OEXP", "OASGN" -> true;
      default -> false;
    };
  }

  // ── Dedup key ─────────────────────────────────────────────────────────────

  /**
   * Assigns sequence numbers within groups of identical rows and returns the dedup key for each.
   *
   * <p>Input list order must match CSV row order so sequences are stable across re-imports.
   */
  static List<String> computeDedupKeys(String account, List<RawRow> rows) {
    Map<String, Integer> groupCounters = new HashMap<>();
    List<String> keys = new ArrayList<>(rows.size());
    for (RawRow row : rows) {
      String base =
          sha256Hex(
              String.join(
                  "|",
                  account,
                  row.activityDate(),
                  row.processDate(),
                  row.settleDate(),
                  row.instrument(),
                  row.description().replaceAll("\\r?\\n", " ").trim(),
                  row.transCode(),
                  row.rawQuantity(),
                  row.rawPrice(),
                  row.rawAmount()));
      int seq = groupCounters.getOrDefault(base, 0);
      groupCounters.put(base, seq + 1);
      keys.add(sha256Hex(base + "|" + seq));
    }
    return keys;
  }

  // ── Public API ────────────────────────────────────────────────────────────

  @Override
  @Transactional(readOnly = true)
  public ImportPreviewResponse preview(MultipartFile file, String account) {
    guardDemo();
    long userId = UserContext.current().userId();
    Set<String> existing = existingDedupKeys(userId);
    List<RawRow> rows = parseCsvRows(file);
    List<String> dedupKeys = computeDedupKeys(account, rows);
    List<ImportRowResult> results = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      results.add(classifyRow(rows.get(i), dedupKeys.get(i), existing, account));
    }
    return new ImportPreviewResponse().rows(results).summary(summarise(results));
  }

  @Override
  public ImportConfirmResponse confirm(MultipartFile file, String account) {
    guardDemo();
    long userId = UserContext.current().userId();
    Set<String> existing = existingDedupKeys(userId);
    List<RawRow> rows = parseCsvRows(file);
    List<String> dedupKeys = computeDedupKeys(account, rows);

    List<ImportRowResult> classified = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      classified.add(classifyRow(rows.get(i), dedupKeys.get(i), existing, account));
    }

    List<String> errors =
        classified.stream()
            .filter(r -> ImportRowResult.ActionEnum.ERROR == r.getAction())
            .map(r -> "Row " + r.getRowNumber() + ": " + r.getError())
            .toList();

    if (!errors.isEmpty()) {
      return new ImportConfirmResponse()
          .tradesImported(0)
          .cashEventsImported(0)
          .duplicatesSkipped(0)
          .errors(errors);
    }

    int tradesImported = 0;
    int cashImported = 0;
    int dupes = 0;

    for (int i = 0; i < classified.size(); i++) {
      ImportRowResult result = classified.get(i);
      RawRow raw = rows.get(i);
      String dedupKey = dedupKeys.get(i);
      switch (result.getAction()) {
        case IMPORT_TRADE -> {
          insertTrade(raw, account, dedupKey, userId);
          tradesImported++;
        }
        case IMPORT_CASH_EVENT -> {
          insertCashEvent(raw, account, dedupKey, userId);
          cashImported++;
        }
        case SKIP_DUPLICATE -> dupes++;
        default -> {
          // SKIP_UNSUPPORTED — no action
        }
      }
    }

    return new ImportConfirmResponse()
        .tradesImported(tradesImported)
        .cashEventsImported(cashImported)
        .duplicatesSkipped(dupes)
        .errors(List.of());
  }

  // ── Internal pipeline ─────────────────────────────────────────────────────

  private void guardDemo() {
    if (UserContext.current().isDemo()) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Demo accounts cannot import data");
    }
  }

  private Set<String> existingDedupKeys(long userId) {
    Set<String> keys = new HashSet<>();
    keys.addAll(tradeRepository.findImportDedupKeysByUserId(userId));
    keys.addAll(financeTransactionRepository.findImportDedupKeysByUserId(userId));
    return keys;
  }

  private List<RawRow> parseCsvRows(MultipartFile file) {
    try (CSVReader reader =
        new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      List<String[]> all = reader.readAll();
      List<RawRow> rows = new ArrayList<>();
      // Row 0 is the header; skip it. Skip blank rows and the footer disclaimer row.
      for (int i = 1; i < all.size(); i++) {
        String[] cols = all.get(i);
        if (cols.length < 9) continue; // blank or footer
        if (cols[0].isBlank()) continue; // blank activity date = footer
        rows.add(
            new RawRow(
                i, // rowNumber is 1-based index in original file (header=0)
                cols[0].trim(),
                cols[1].trim(),
                cols[2].trim(),
                cols[3].trim(),
                cols[4].trim(),
                cols[5].trim(),
                cols[6].trim(),
                cols[7].trim(),
                cols[8].trim()));
      }
      return rows;
    } catch (IOException | CsvException e) {
      throw new IllegalArgumentException("Could not parse CSV: " + e.getMessage(), e);
    }
  }

  private ImportRowResult classifyRow(
      RawRow row, String dedupKey, Set<String> existing, String account) {
    ImportRowResult result = new ImportRowResult();
    result.setRowNumber(row.rowNumber());
    result.setActivityDate(row.activityDate());
    result.setInstrument(row.instrument());
    result.setTransCode(row.transCode());

    if (existing.contains(dedupKey)) {
      return result
          .action(ImportRowResult.ActionEnum.SKIP_DUPLICATE)
          .detail("Already imported")
          .error(null);
    }

    String tradeSide = tradeSideFor(row.transCode());
    if (tradeSide != null) {
      return classifyTradeRow(row, result, tradeSide);
    }

    if (isCashEvent(row.transCode())) {
      return classifyCashRow(row, result);
    }

    return result
        .action(ImportRowResult.ActionEnum.SKIP_UNSUPPORTED)
        .detail("Trans code " + row.transCode() + " — not imported")
        .error(null);
  }

  private ImportRowResult classifyTradeRow(RawRow row, ImportRowResult result, String side) {
    boolean isOption = isOptionTransCode(row.transCode());

    if (isOption) {
      OptionDetails opt = parseOptionDescription(row.description());
      if (opt == null) {
        return result
            .action(ImportRowResult.ActionEnum.ERROR)
            .detail("Could not parse option description")
            .error("Unrecognised option description: \"" + firstLine(row.description()) + "\"");
      }
      String detail =
          String.format(
              "%s OPTION · %s %s $%s exp %s",
              side, row.instrument(), opt.optionType(), opt.strikePrice(), opt.expirationDate());
      return result.action(ImportRowResult.ActionEnum.IMPORT_TRADE).detail(detail).error(null);
    }

    // Equity — validate quantity
    if (row.rawQuantity().isBlank()) {
      return result
          .action(ImportRowResult.ActionEnum.ERROR)
          .detail("Missing quantity")
          .error("quantity is blank for equity row");
    }
    String detail = String.format("%s EQUITY · %s × %s", side, row.instrument(), row.rawQuantity());
    return result.action(ImportRowResult.ActionEnum.IMPORT_TRADE).detail(detail).error(null);
  }

  private ImportRowResult classifyCashRow(RawRow row, ImportRowResult result) {
    String desc =
        switch (row.transCode()) {
          case "CDIV" -> row.instrument() + " Cash Dividend";
          case "SLIP" -> row.instrument() + " Stock Lending Income";
          case "INT" -> "Interest Payment";
          default -> row.transCode();
        };
    return result.action(ImportRowResult.ActionEnum.IMPORT_CASH_EVENT).detail(desc).error(null);
  }

  private void insertTrade(RawRow row, String account, String dedupKey, long userId) {
    String side = tradeSideFor(row.transCode());
    boolean isOption = isOptionTransCode(row.transCode());
    String assetType = isOption ? "OPTION" : "EQUITY";
    int multiplier = isOption ? 100 : 1;

    BigDecimal qty;
    BigDecimal price;
    if ("EXPIRE".equals(side) || "EXERCISE".equals(side)) {
      qty = new BigDecimal(row.rawQuantity().isBlank() ? "0" : row.rawQuantity());
      price = BigDecimal.ZERO;
    } else {
      qty = new BigDecimal(row.rawQuantity());
      price = parseAmount(row.rawPrice().isBlank() ? null : "$" + row.rawPrice());
      if (price == null) price = BigDecimal.ZERO;
    }

    LocalDate tradeDate = LocalDate.parse(row.activityDate(), RH_DATE);

    String optionType = null;
    BigDecimal strikePrice = null;
    LocalDate expirationDate = null;
    if (isOption) {
      OptionDetails opt = parseOptionDescription(row.description());
      if (opt != null) {
        optionType = opt.optionType();
        strikePrice = opt.strikePrice();
        expirationDate = opt.expirationDate();
      }
    }

    TradeEntity entity =
        new TradeEntity(
            userId,
            row.instrument(),
            side,
            qty,
            price,
            tradeDate,
            null,
            assetType,
            optionType,
            strikePrice,
            expirationDate,
            multiplier);
    entity.setAccount(account);
    entity.setImportDedupKey(dedupKey);
    tradeRepository.save(entity);
    portfolioService.applyTrade(row.instrument(), side, assetType, qty, price);
  }

  private void insertCashEvent(RawRow row, String account, String dedupKey, long userId) {
    BigDecimal amount = parseAmount(row.rawAmount());
    if (amount == null) amount = BigDecimal.ZERO;
    LocalDate date = LocalDate.parse(row.activityDate(), RH_DATE);
    Instant postedAt = date.atStartOfDay(ZoneOffset.UTC).toInstant();
    String description =
        switch (row.transCode()) {
          case "CDIV" -> row.instrument() + " Cash Dividend";
          case "SLIP" -> row.instrument() + " Stock Lending Income";
          default -> "Interest Payment";
        };
    String notes = firstLine(row.description());
    FinanceTransactionEntity entity =
        new FinanceTransactionEntity(userId, postedAt, description, amount, null, notes);
    entity.setAccount(account);
    entity.setImportDedupKey(dedupKey);
    financeTransactionRepository.save(entity);
  }

  private ImportPreviewSummary summarise(List<ImportRowResult> results) {
    int trades = 0, cash = 0, dupes = 0, unsupported = 0, errors = 0;
    for (ImportRowResult r : results) {
      switch (r.getAction()) {
        case IMPORT_TRADE -> trades++;
        case IMPORT_CASH_EVENT -> cash++;
        case SKIP_DUPLICATE -> dupes++;
        case SKIP_UNSUPPORTED -> unsupported++;
        case ERROR -> errors++;
      }
    }
    return new ImportPreviewSummary()
        .tradesToImport(trades)
        .cashEventsToImport(cash)
        .duplicatesSkipped(dupes)
        .unsupportedSkipped(unsupported)
        .errors(errors);
  }

  private static String firstLine(String s) {
    if (s == null) return "";
    int nl = s.indexOf('\n');
    return nl >= 0 ? s.substring(0, nl).trim() : s.trim();
  }
}
