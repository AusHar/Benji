# Options Trade Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add options trade tracking (calls, puts, covered calls) alongside existing equity trades with EXPIRE/EXERCISE handling and auto-created linked equity trades.

**Architecture:** Extend the existing `trades` table with nullable option columns (Flyway V6). Refactor the FIFO lot matcher to support bidirectional matching (long and short positions) grouped by contract key. EXERCISE auto-creates linked equity trades. Frontend modal gains an asset type toggle with contextual fields. All changes are backward compatible — existing equity trades are unaffected.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Data JPA, Flyway, OpenAPI codegen (spring generator), Single-file HTML/CSS/JS SPA, JUnit 5, Testcontainers Postgres

---

### Task 1: OpenAPI Spec — Add Option Fields to Trade Schemas

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/openAPI.yaml`

Update the OpenAPI spec first (OpenAPI-first workflow). This generates the DTOs that all other code depends on.

- [ ] **Step 1: Update `LogTradeRequest` schema**

In `openAPI.yaml`, replace the `LogTradeRequest` schema (lines ~1239-1262) with:

```yaml
    LogTradeRequest:
      type: object
      required: [ticker, side]
      properties:
        ticker:
          type: string
          minLength: 1
          maxLength: 12
        side:
          type: string
          enum: [BUY, SELL, EXPIRE, EXERCISE]
        quantity:
          type: number
          format: double
          minimum: 0
        pricePerShare:
          type: number
          format: double
          minimum: 0
        tradeDate:
          type: string
          format: date
        notes:
          type: string
        assetType:
          type: string
          enum: [EQUITY, OPTION]
        optionType:
          type: string
          enum: [CALL, PUT]
        strikePrice:
          type: number
          format: double
          minimum: 0.0001
        expirationDate:
          type: string
          format: date
```

- [ ] **Step 2: Update `Trade` response schema**

Replace the `Trade` schema (lines ~1264-1288) with:

```yaml
    Trade:
      type: object
      properties:
        id:
          type: integer
          format: int64
        ticker:
          type: string
        side:
          type: string
          enum: [BUY, SELL, EXPIRE, EXERCISE]
        quantity:
          type: number
          format: double
        pricePerShare:
          type: number
          format: double
        tradeDate:
          type: string
          format: date
        notes:
          type: string
        createdAt:
          type: string
          format: date-time
        assetType:
          type: string
          enum: [EQUITY, OPTION]
        optionType:
          type: string
          enum: [CALL, PUT]
        strikePrice:
          type: number
          format: double
        expirationDate:
          type: string
          format: date
        multiplier:
          type: integer
        linkedTradeId:
          type: integer
          format: int64
```

- [ ] **Step 3: Update `ClosedTrade` response schema**

Replace the `ClosedTrade` schema (lines ~1298-1325) with:

```yaml
    ClosedTrade:
      type: object
      properties:
        ticker:
          type: string
        quantity:
          type: number
          format: double
        buyPrice:
          type: number
          format: double
        sellPrice:
          type: number
          format: double
        buyDate:
          type: string
          format: date
        sellDate:
          type: string
          format: date
        pnl:
          type: number
          format: double
        pnlPercent:
          type: number
          format: double
        holdDays:
          type: integer
        assetType:
          type: string
          enum: [EQUITY, OPTION]
        optionType:
          type: string
          enum: [CALL, PUT]
        strikePrice:
          type: number
          format: double
        expirationDate:
          type: string
          format: date
```

- [ ] **Step 4: Update the `side` filter enum on `GET /api/trades`**

Change line ~520 from:

```yaml
            enum: [BUY, SELL]
```

To:

```yaml
            enum: [BUY, SELL, EXPIRE, EXERCISE]
```

- [ ] **Step 5: Run codegen and verify**

```bash
cd apps/api/trader-assistant/trading-dashboard && ./gradlew openApiGenerate
```

Expected: BUILD SUCCESSFUL. New enum values and fields appear in generated DTOs under `build/generated/openapi/`.

- [ ] **Step 6: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/openAPI.yaml
git commit -m "feat: add options fields to trade OpenAPI schemas"
```

---

### Task 2: Flyway V6 Migration — Add Option Columns to Trades Table

**Files:**
- Create: `apps/api/trader-assistant/trading-dashboard/src/main/resources/db/migration/V6__options_support.sql`

- [ ] **Step 1: Write the migration**

Create `V6__options_support.sql`:

```sql
-- V6__options_support.sql
-- Options trade support: add option columns to trades table

-- 1. Add new columns
ALTER TABLE trades ADD COLUMN asset_type VARCHAR(6) NOT NULL DEFAULT 'EQUITY';
ALTER TABLE trades ADD COLUMN option_type VARCHAR(4);
ALTER TABLE trades ADD COLUMN strike_price DECIMAL(12,4);
ALTER TABLE trades ADD COLUMN expiration_date DATE;
ALTER TABLE trades ADD COLUMN multiplier INTEGER NOT NULL DEFAULT 1;
ALTER TABLE trades ADD COLUMN linked_trade_id BIGINT;

-- 2. Expand side constraint: drop old, add new
-- The V4 inline CHECK is unnamed in H2 but named in Postgres.
-- Use a column redefine approach that works in both:
ALTER TABLE trades ALTER COLUMN side VARCHAR(8) NOT NULL;

-- 3. Add new constraints
ALTER TABLE trades ADD CONSTRAINT trades_asset_type_check
    CHECK (asset_type IN ('EQUITY', 'OPTION'));
ALTER TABLE trades ADD CONSTRAINT trades_option_type_check
    CHECK (option_type IS NULL OR option_type IN ('CALL', 'PUT'));
ALTER TABLE trades ADD CONSTRAINT trades_linked_trade_fk
    FOREIGN KEY (linked_trade_id) REFERENCES trades(id);

-- 4. Index for contract identity lookups (lot matching)
CREATE INDEX idx_trades_contract
    ON trades (user_id, ticker, asset_type, option_type, strike_price, expiration_date);
```

Note: The original V4 `CHECK (side IN ('BUY', 'SELL'))` was an inline constraint. Widening the column to `VARCHAR(8)` (to fit 'EXERCISE') removes the inline check in both H2 and Postgres. The new valid side values are enforced at the application layer rather than adding a new CHECK constraint — this avoids H2/Postgres incompatibility issues with named constraint drops. The JPA entity validates side values before persisting.

- [ ] **Step 2: Verify migration runs on H2 (dev profile)**

```bash
cd apps/api/trader-assistant/trading-dashboard && ./gradlew bootRun -Dspring.profiles.active=dev
```

Expected: App starts without Flyway errors. (Dev profile has Flyway disabled, so this just verifies no startup issues.)

- [ ] **Step 3: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/src/main/resources/db/migration/V6__options_support.sql
git commit -m "feat: add V6 Flyway migration for options trade columns"
```

---

### Task 3: Entity & Record — Extend TradeEntity and ClosedTrade

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/persistence/TradeEntity.java`
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/trades/ClosedTrade.java`

- [ ] **Step 1: Add option fields to TradeEntity**

Replace the entire `TradeEntity.java` with:

```java
package com.austinharlan.trading_dashboard.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "trades")
public class TradeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "ticker", nullable = false, length = 12)
  private String ticker;

  @Column(name = "side", nullable = false, length = 8)
  private String side;

  @Column(name = "quantity", nullable = false, precision = 12, scale = 4)
  private BigDecimal quantity;

  @Column(name = "price_per_share", nullable = false, precision = 12, scale = 4)
  private BigDecimal pricePerShare;

  @Column(name = "trade_date", nullable = false)
  private LocalDate tradeDate;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "asset_type", nullable = false, length = 6)
  private String assetType;

  @Column(name = "option_type", length = 4)
  private String optionType;

  @Column(name = "strike_price", precision = 12, scale = 4)
  private BigDecimal strikePrice;

  @Column(name = "expiration_date")
  private LocalDate expirationDate;

  @Column(name = "multiplier", nullable = false)
  private int multiplier;

  @Column(name = "linked_trade_id")
  private Long linkedTradeId;

  protected TradeEntity() {}

  /** Constructor for equity trades (backward compatible). */
  public TradeEntity(
      Long userId,
      String ticker,
      String side,
      BigDecimal quantity,
      BigDecimal pricePerShare,
      LocalDate tradeDate,
      String notes) {
    this(userId, ticker, side, quantity, pricePerShare, tradeDate, notes,
         "EQUITY", null, null, null, 1);
  }

  /** Full constructor for option trades. */
  public TradeEntity(
      Long userId,
      String ticker,
      String side,
      BigDecimal quantity,
      BigDecimal pricePerShare,
      LocalDate tradeDate,
      String notes,
      String assetType,
      String optionType,
      BigDecimal strikePrice,
      LocalDate expirationDate,
      int multiplier) {
    this.userId = Objects.requireNonNull(userId, "userId must not be null");
    this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
    this.side = Objects.requireNonNull(side, "side must not be null");
    this.quantity = Objects.requireNonNull(quantity, "quantity must not be null");
    this.pricePerShare = Objects.requireNonNull(pricePerShare, "pricePerShare must not be null");
    this.tradeDate = Objects.requireNonNull(tradeDate, "tradeDate must not be null");
    this.notes = notes;
    this.createdAt = Instant.now();
    this.assetType = Objects.requireNonNull(assetType, "assetType must not be null");
    this.optionType = optionType;
    this.strikePrice = strikePrice;
    this.expirationDate = expirationDate;
    this.multiplier = multiplier;
  }

  public Long getId() { return id; }
  public Long getUserId() { return userId; }
  public String getTicker() { return ticker; }
  public String getSide() { return side; }
  public BigDecimal getQuantity() { return quantity; }
  public BigDecimal getPricePerShare() { return pricePerShare; }
  public LocalDate getTradeDate() { return tradeDate; }
  public String getNotes() { return notes; }
  public Instant getCreatedAt() { return createdAt; }
  public String getAssetType() { return assetType; }
  public String getOptionType() { return optionType; }
  public BigDecimal getStrikePrice() { return strikePrice; }
  public LocalDate getExpirationDate() { return expirationDate; }
  public int getMultiplier() { return multiplier; }
  public Long getLinkedTradeId() { return linkedTradeId; }

  public void setLinkedTradeId(Long linkedTradeId) {
    this.linkedTradeId = linkedTradeId;
  }

  public void setQuantity(BigDecimal quantity) {
    this.quantity = quantity;
  }

  public void setPricePerShare(BigDecimal pricePerShare) {
    this.pricePerShare = pricePerShare;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TradeEntity that)) return false;
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
```

- [ ] **Step 2: Extend ClosedTrade record**

Replace `ClosedTrade.java` with:

```java
package com.austinharlan.trading_dashboard.trades;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClosedTrade(
    String ticker,
    BigDecimal quantity,
    BigDecimal buyPrice,
    BigDecimal sellPrice,
    LocalDate buyDate,
    LocalDate sellDate,
    BigDecimal pnl,
    BigDecimal pnlPercent,
    long holdDays,
    String assetType,
    String optionType,
    BigDecimal strikePrice,
    LocalDate expirationDate) {}
```

- [ ] **Step 3: Run spotless**

```bash
cd apps/api/trader-assistant/trading-dashboard && ./gradlew spotlessApply
```

- [ ] **Step 4: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/persistence/TradeEntity.java apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/trades/ClosedTrade.java
git commit -m "feat: extend TradeEntity and ClosedTrade with option fields"
```

---

### Task 4: Service Layer — Bidirectional Lot Matcher and Validation

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/service/TradeService.java`
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/service/DefaultTradeService.java`

This is the most complex task. The lot matcher refactor, EXPIRE/EXERCISE handling, and validation all live here.

- [ ] **Step 1: Update TradeService interface**

Replace `logTrade` signature in `TradeService.java` to accept option fields:

```java
package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.persistence.TradeEntity;
import com.austinharlan.trading_dashboard.trades.ClosedTrade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.lang.Nullable;

public interface TradeService {

  TradeEntity logTrade(
      String ticker,
      String side,
      @Nullable BigDecimal quantity,
      @Nullable BigDecimal pricePerShare,
      @Nullable LocalDate tradeDate,
      @Nullable String notes,
      @Nullable String assetType,
      @Nullable String optionType,
      @Nullable BigDecimal strikePrice,
      @Nullable LocalDate expirationDate);

  List<TradeEntity> listTrades(
      @Nullable String ticker,
      @Nullable String side,
      @Nullable LocalDate from,
      @Nullable LocalDate to);

  TradeEntity getTrade(long id);

  void deleteTrade(long id);

  List<ClosedTrade> getClosedTrades();

  TradeStats getStats();

  List<PnlHistoryEntry> getPnlHistory();

  List<TradeCalendarEntry> getTradeCalendar();

  record TradeStats(
      int totalTrades,
      int wins,
      int losses,
      double winRate,
      BigDecimal totalPnl,
      int currentStreak,
      String currentStreakType,
      int bestWinStreak,
      int bestLossStreak,
      double avgHoldDays,
      List<TickerPnl> topTickers) {}

  record TickerPnl(String ticker, BigDecimal pnl, int tradeCount) {}

  record PnlHistoryEntry(LocalDate date, BigDecimal pnl, BigDecimal cumulativePnl) {}

  record TradeCalendarEntry(LocalDate date, BigDecimal pnl, int tradeCount) {}
}
```

- [ ] **Step 2: Rewrite DefaultTradeService with bidirectional matcher and validation**

Replace the entire `DefaultTradeService.java` with the following. Key changes: `logTrade` validates per side/assetType, `computeClosedTrades` uses bidirectional queues grouped by `ContractKey`, and EXPIRE/EXERCISE are handled as special closing events.

```java
package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.config.UserContext;
import com.austinharlan.trading_dashboard.persistence.TradeEntity;
import com.austinharlan.trading_dashboard.persistence.TradeRepository;
import com.austinharlan.trading_dashboard.trades.ClosedTrade;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultTradeService implements TradeService {

  private final TradeRepository repository;

  public DefaultTradeService(TradeRepository repository) {
    this.repository = repository;
  }

  @Override
  public TradeEntity logTrade(
      String ticker,
      String side,
      @Nullable BigDecimal quantity,
      @Nullable BigDecimal pricePerShare,
      @Nullable LocalDate tradeDate,
      @Nullable String notes,
      @Nullable String assetType,
      @Nullable String optionType,
      @Nullable BigDecimal strikePrice,
      @Nullable LocalDate expirationDate) {

    long userId = UserContext.current().userId();
    LocalDate date = tradeDate != null ? tradeDate : LocalDate.now();
    String type = assetType != null ? assetType : "EQUITY";
    int multiplier = "OPTION".equals(type) ? 100 : 1;

    validate(side, type, quantity, pricePerShare, optionType, strikePrice, expirationDate);

    if ("EXPIRE".equals(side)) {
      return handleExpire(userId, ticker, date, notes, optionType, strikePrice, expirationDate);
    }
    if ("EXERCISE".equals(side)) {
      return handleExercise(
          userId, ticker, date, notes, quantity, optionType, strikePrice, expirationDate);
    }

    TradeEntity entity =
        new TradeEntity(
            userId, ticker, side, quantity, pricePerShare, date, notes,
            type, optionType, strikePrice, expirationDate, multiplier);
    return repository.save(entity);
  }

  private void validate(
      String side,
      String assetType,
      @Nullable BigDecimal quantity,
      @Nullable BigDecimal pricePerShare,
      @Nullable String optionType,
      @Nullable BigDecimal strikePrice,
      @Nullable LocalDate expirationDate) {

    if ("EXPIRE".equals(side) || "EXERCISE".equals(side)) {
      if (!"OPTION".equals(assetType)) {
        throw new IllegalArgumentException(side + " is only valid for OPTION trades");
      }
      requireOptionFields(optionType, strikePrice, expirationDate);
      if ("EXERCISE".equals(side)) {
        requirePositive(quantity, "quantity");
      }
      return;
    }

    // BUY or SELL
    requirePositive(quantity, "quantity");
    requirePositive(pricePerShare, "pricePerShare");

    if ("OPTION".equals(assetType)) {
      requireOptionFields(optionType, strikePrice, expirationDate);
    } else {
      if (optionType != null || strikePrice != null || expirationDate != null) {
        throw new IllegalArgumentException("Option fields must be null for EQUITY trades");
      }
    }
  }

  private void requireOptionFields(
      @Nullable String optionType,
      @Nullable BigDecimal strikePrice,
      @Nullable LocalDate expirationDate) {
    if (optionType == null || strikePrice == null || expirationDate == null) {
      throw new IllegalArgumentException(
          "optionType, strikePrice, and expirationDate are required for OPTION trades");
    }
  }

  private void requirePositive(@Nullable BigDecimal value, String field) {
    if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException(field + " must be greater than 0");
    }
  }

  private TradeEntity handleExpire(
      long userId,
      String ticker,
      LocalDate date,
      @Nullable String notes,
      String optionType,
      BigDecimal strikePrice,
      LocalDate expirationDate) {

    List<TradeEntity> all = repository.findAllChronologicalByUserId(userId);
    ContractKey key = new ContractKey(ticker, "OPTION", optionType, strikePrice, expirationDate);
    BigDecimal remainingQty = computeRemainingQuantity(all, key);

    if (remainingQty.compareTo(BigDecimal.ZERO) == 0) {
      throw new IllegalArgumentException("No open lots found for this contract to expire");
    }

    TradeEntity expire =
        new TradeEntity(
            userId, ticker, "EXPIRE", remainingQty, BigDecimal.ZERO, date, notes,
            "OPTION", optionType, strikePrice, expirationDate, 100);
    return repository.save(expire);
  }

  private TradeEntity handleExercise(
      long userId,
      String ticker,
      LocalDate date,
      @Nullable String notes,
      BigDecimal quantity,
      String optionType,
      BigDecimal strikePrice,
      LocalDate expirationDate) {

    // Determine if long or short position by checking which side has open lots
    List<TradeEntity> all = repository.findAllChronologicalByUserId(userId);
    ContractKey key = new ContractKey(ticker, "OPTION", optionType, strikePrice, expirationDate);
    boolean isLong = isLongPosition(all, key);

    // Save the option EXERCISE trade
    TradeEntity exercise =
        new TradeEntity(
            userId, ticker, "EXERCISE", quantity, BigDecimal.ZERO, date, notes,
            "OPTION", optionType, strikePrice, expirationDate, 100);
    exercise = repository.save(exercise);

    // Determine linked equity side and create it
    BigDecimal shares = quantity.multiply(BigDecimal.valueOf(100));
    String equitySide;
    if ("CALL".equals(optionType)) {
      equitySide = isLong ? "BUY" : "SELL";
    } else {
      equitySide = isLong ? "SELL" : "BUY";
    }
    String equityNotes = String.format(
        "Auto-created from %s exercise on %s $%s%s %s",
        optionType, ticker, strikePrice.stripTrailingZeros().toPlainString(),
        "CALL".equals(optionType) ? "C" : "P",
        String.format("%tD", expirationDate));

    TradeEntity equity =
        new TradeEntity(
            userId, ticker, equitySide, shares, strikePrice, date, equityNotes);
    equity = repository.save(equity);

    // Link bidirectionally
    exercise.setLinkedTradeId(equity.getId());
    equity.setLinkedTradeId(exercise.getId());
    repository.save(exercise);
    repository.save(equity);

    return exercise;
  }

  private boolean isLongPosition(List<TradeEntity> allTrades, ContractKey key) {
    BigDecimal buyQty = BigDecimal.ZERO;
    BigDecimal sellQty = BigDecimal.ZERO;
    for (TradeEntity t : allTrades) {
      if (!key.matches(t)) continue;
      if ("BUY".equals(t.getSide())) {
        buyQty = buyQty.add(t.getQuantity());
      } else if ("SELL".equals(t.getSide())) {
        sellQty = sellQty.add(t.getQuantity());
      }
    }
    return buyQty.compareTo(sellQty) > 0;
  }

  private BigDecimal computeRemainingQuantity(List<TradeEntity> allTrades, ContractKey key) {
    BigDecimal buyQty = BigDecimal.ZERO;
    BigDecimal sellQty = BigDecimal.ZERO;
    for (TradeEntity t : allTrades) {
      if (!key.matches(t)) continue;
      String s = t.getSide();
      if ("BUY".equals(s)) {
        buyQty = buyQty.add(t.getQuantity());
      } else if ("SELL".equals(s)) {
        sellQty = sellQty.add(t.getQuantity());
      }
      // EXPIRE/EXERCISE already matched against open lots, so they close qty too
      if ("EXPIRE".equals(s) || "EXERCISE".equals(s)) {
        // These reduce the majority side
        if (buyQty.compareTo(sellQty) > 0) {
          sellQty = sellQty.add(t.getQuantity());
        } else {
          buyQty = buyQty.add(t.getQuantity());
        }
      }
    }
    return buyQty.subtract(sellQty).abs();
  }

  @Override
  @Transactional(readOnly = true)
  public List<TradeEntity> listTrades(
      @Nullable String ticker,
      @Nullable String side,
      @Nullable LocalDate from,
      @Nullable LocalDate to) {
    long userId = UserContext.current().userId();
    if (ticker == null && side == null && from == null && to == null) {
      return repository.findAllByUserIdOrderByTradeDateDescCreatedAtDesc(userId);
    }
    return repository.findFilteredByUserId(userId, ticker, side, from, to);
  }

  @Override
  @Transactional(readOnly = true)
  public TradeEntity getTrade(long id) {
    long userId = UserContext.current().userId();
    TradeEntity entity = repository.findById(id).orElseThrow(() -> notFound(id));
    if (!entity.getUserId().equals(userId)) {
      throw new EntityNotFoundException("Trade not found: " + id);
    }
    return entity;
  }

  @Override
  public void deleteTrade(long id) {
    long userId = UserContext.current().userId();
    TradeEntity entity = repository.findById(id).orElseThrow(() -> notFound(id));
    if (!entity.getUserId().equals(userId)) {
      throw new EntityNotFoundException("Trade not found: " + id);
    }
    repository.deleteById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ClosedTrade> getClosedTrades() {
    long userId = UserContext.current().userId();
    List<TradeEntity> all = repository.findAllChronologicalByUserId(userId);
    return computeClosedTrades(all);
  }

  @Override
  @Transactional(readOnly = true)
  public TradeStats getStats() {
    List<ClosedTrade> closed = getClosedTrades();
    if (closed.isEmpty()) {
      return new TradeStats(0, 0, 0, 0.0, BigDecimal.ZERO, 0, "NONE", 0, 0, 0.0, List.of());
    }

    int wins = 0, losses = 0;
    BigDecimal totalPnl = BigDecimal.ZERO;
    long totalHoldDays = 0;
    int bestWinStreak = 0, bestLossStreak = 0;
    int runWin = 0, runLoss = 0;

    List<ClosedTrade> sorted =
        closed.stream().sorted(Comparator.comparing(ClosedTrade::sellDate)).toList();

    for (ClosedTrade ct : sorted) {
      totalPnl = totalPnl.add(ct.pnl());
      totalHoldDays += ct.holdDays();
      if (ct.pnl().compareTo(BigDecimal.ZERO) > 0) {
        wins++;
        runWin++;
        runLoss = 0;
        bestWinStreak = Math.max(bestWinStreak, runWin);
      } else {
        losses++;
        runLoss++;
        runWin = 0;
        bestLossStreak = Math.max(bestLossStreak, runLoss);
      }
    }

    int total = wins + losses;
    double winRate = total > 0 ? (double) wins / total * 100.0 : 0.0;
    double avgHold = total > 0 ? (double) totalHoldDays / total : 0.0;
    int currentStreak = Math.max(runWin, runLoss);
    String currentStreakType = runWin > 0 ? "WIN" : (runLoss > 0 ? "LOSS" : "NONE");

    Map<String, BigDecimal> pnlByTicker = new LinkedHashMap<>();
    Map<String, Integer> countByTicker = new LinkedHashMap<>();
    for (ClosedTrade ct : closed) {
      pnlByTicker.merge(ct.ticker(), ct.pnl(), BigDecimal::add);
      countByTicker.merge(ct.ticker(), 1, Integer::sum);
    }
    List<TickerPnl> topTickers =
        pnlByTicker.entrySet().stream()
            .sorted(
                Comparator.comparing(
                    (Map.Entry<String, BigDecimal> e) -> e.getValue().abs(),
                    Comparator.reverseOrder()))
            .limit(5)
            .map(e -> new TickerPnl(e.getKey(), e.getValue(), countByTicker.get(e.getKey())))
            .toList();

    return new TradeStats(
        total, wins, losses, winRate, totalPnl, currentStreak, currentStreakType,
        bestWinStreak, bestLossStreak, avgHold, topTickers);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PnlHistoryEntry> getPnlHistory() {
    List<ClosedTrade> closed = getClosedTrades();
    List<ClosedTrade> sorted =
        closed.stream().sorted(Comparator.comparing(ClosedTrade::sellDate)).toList();
    List<PnlHistoryEntry> history = new ArrayList<>();
    BigDecimal cumulative = BigDecimal.ZERO;
    for (ClosedTrade ct : sorted) {
      cumulative = cumulative.add(ct.pnl());
      history.add(new PnlHistoryEntry(ct.sellDate(), ct.pnl(), cumulative));
    }
    return history;
  }

  @Override
  @Transactional(readOnly = true)
  public List<TradeCalendarEntry> getTradeCalendar() {
    List<ClosedTrade> closed = getClosedTrades();
    Map<LocalDate, BigDecimal> pnlByDate = new LinkedHashMap<>();
    Map<LocalDate, Integer> countByDate = new LinkedHashMap<>();
    for (ClosedTrade ct : closed) {
      pnlByDate.merge(ct.sellDate(), ct.pnl(), BigDecimal::add);
      countByDate.merge(ct.sellDate(), 1, Integer::sum);
    }
    return pnlByDate.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> new TradeCalendarEntry(e.getKey(), e.getValue(), countByDate.get(e.getKey())))
        .toList();
  }

  // ── Bidirectional Lot Matcher ────────────────────────────────────────────

  static List<ClosedTrade> computeClosedTrades(List<TradeEntity> trades) {
    Map<ContractKey, List<TradeEntity>> byContract =
        trades.stream()
            .collect(
                Collectors.groupingBy(
                    ContractKey::from, LinkedHashMap::new, Collectors.toList()));

    List<ClosedTrade> result = new ArrayList<>();
    for (Map.Entry<ContractKey, List<TradeEntity>> entry : byContract.entrySet()) {
      ContractKey key = entry.getKey();
      Deque<Lot> buyQueue = new ArrayDeque<>();
      Deque<Lot> sellQueue = new ArrayDeque<>();

      for (TradeEntity t : entry.getValue()) {
        String side = t.getSide();
        int mult = t.getMultiplier();

        if ("EXPIRE".equals(side)) {
          // Close ALL remaining lots at $0
          closeAllLots(buyQueue, t, key, mult, result);
          closeAllLots(sellQueue, t, key, mult, result);
          continue;
        }

        if ("EXERCISE".equals(side)) {
          // Close specified quantity from whichever side has open lots
          Deque<Lot> openQueue = !buyQueue.isEmpty() ? buyQueue : sellQueue;
          matchLots(openQueue, t.getQuantity(), BigDecimal.ZERO, t.getTradeDate(), key, mult,
              result);
          continue;
        }

        boolean isBuy = "BUY".equals(side);
        Deque<Lot> oppositeQueue = isBuy ? sellQueue : buyQueue;
        Deque<Lot> sameQueue = isBuy ? buyQueue : sellQueue;

        if (!oppositeQueue.isEmpty()) {
          // Closing: match against opposite side
          matchLots(oppositeQueue, t.getQuantity(), t.getPricePerShare(), t.getTradeDate(), key,
              mult, result);
        } else {
          // Opening: add to same side queue
          sameQueue.addLast(new Lot(t.getQuantity(), t.getPricePerShare(), t.getTradeDate(),
              isBuy));
        }
      }
    }
    return result;
  }

  private static void closeAllLots(
      Deque<Lot> queue, TradeEntity closingTrade, ContractKey key, int multiplier,
      List<ClosedTrade> result) {
    while (!queue.isEmpty()) {
      Lot lot = queue.pollFirst();
      BigDecimal buyPrice = lot.isBuy ? lot.price : BigDecimal.ZERO;
      BigDecimal sellPrice = lot.isBuy ? BigDecimal.ZERO : lot.price;
      LocalDate buyDate = lot.isBuy ? lot.date : closingTrade.getTradeDate();
      LocalDate sellDate = lot.isBuy ? closingTrade.getTradeDate() : lot.date;
      BigDecimal pnl =
          sellPrice.subtract(buyPrice).multiply(lot.remaining).multiply(
              BigDecimal.valueOf(multiplier));
      BigDecimal pnlPct = computePnlPercent(buyPrice, sellPrice);
      long holdDays = Math.abs(ChronoUnit.DAYS.between(buyDate, sellDate));
      result.add(new ClosedTrade(
          key.ticker, lot.remaining, buyPrice, sellPrice, buyDate, sellDate,
          pnl, pnlPct, holdDays, key.assetType, key.optionType, key.strikePrice,
          key.expirationDate));
    }
  }

  private static void matchLots(
      Deque<Lot> openQueue, BigDecimal closeQty, BigDecimal closePrice, LocalDate closeDate,
      ContractKey key, int multiplier, List<ClosedTrade> result) {
    BigDecimal remaining = closeQty;
    while (remaining.compareTo(BigDecimal.ZERO) > 0 && !openQueue.isEmpty()) {
      Lot lot = openQueue.peekFirst();
      BigDecimal matched = remaining.min(lot.remaining);

      BigDecimal buyPrice = lot.isBuy ? lot.price : closePrice;
      BigDecimal sellPrice = lot.isBuy ? closePrice : lot.price;
      LocalDate buyDate = lot.isBuy ? lot.date : closeDate;
      LocalDate sellDate = lot.isBuy ? closeDate : lot.date;
      BigDecimal pnl =
          sellPrice.subtract(buyPrice).multiply(matched).multiply(
              BigDecimal.valueOf(multiplier));
      BigDecimal pnlPct = computePnlPercent(buyPrice, sellPrice);
      long holdDays = Math.abs(ChronoUnit.DAYS.between(buyDate, sellDate));

      result.add(new ClosedTrade(
          key.ticker, matched, buyPrice, sellPrice, buyDate, sellDate,
          pnl, pnlPct, holdDays, key.assetType, key.optionType, key.strikePrice,
          key.expirationDate));

      lot.remaining = lot.remaining.subtract(matched);
      remaining = remaining.subtract(matched);
      if (lot.remaining.compareTo(BigDecimal.ZERO) == 0) {
        openQueue.pollFirst();
      }
    }
  }

  private static BigDecimal computePnlPercent(BigDecimal buyPrice, BigDecimal sellPrice) {
    if (buyPrice.compareTo(BigDecimal.ZERO) == 0) {
      // Short position: percent based on sell (open) price
      if (sellPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
      return sellPrice.subtract(buyPrice)
          .divide(sellPrice, 4, RoundingMode.HALF_UP)
          .multiply(BigDecimal.valueOf(100));
    }
    return sellPrice.subtract(buyPrice)
        .divide(buyPrice, 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100));
  }

  private static EntityNotFoundException notFound(long id) {
    return new EntityNotFoundException("Trade not found: " + id);
  }

  // ── Inner types ──────────────────────────────────────────────────────────

  record ContractKey(
      String ticker,
      String assetType,
      String optionType,
      BigDecimal strikePrice,
      LocalDate expirationDate) {

    static ContractKey from(TradeEntity t) {
      return new ContractKey(
          t.getTicker(), t.getAssetType(), t.getOptionType(),
          t.getStrikePrice(), t.getExpirationDate());
    }

    boolean matches(TradeEntity t) {
      return Objects.equals(ticker, t.getTicker())
          && Objects.equals(assetType, t.getAssetType())
          && Objects.equals(optionType, t.getOptionType())
          && Objects.equals(strikePrice, t.getStrikePrice())
          && Objects.equals(expirationDate, t.getExpirationDate());
    }
  }

  private static class Lot {
    BigDecimal remaining;
    final BigDecimal price;
    final LocalDate date;
    final boolean isBuy;

    Lot(BigDecimal qty, BigDecimal price, LocalDate date, boolean isBuy) {
      this.remaining = qty;
      this.price = price;
      this.date = date;
      this.isBuy = isBuy;
    }
  }
}
```

- [ ] **Step 3: Run spotless**

```bash
cd apps/api/trader-assistant/trading-dashboard && ./gradlew spotlessApply
```

- [ ] **Step 4: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/service/TradeService.java apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/service/DefaultTradeService.java
git commit -m "feat: bidirectional lot matcher with EXPIRE/EXERCISE and validation"
```

---

### Task 5: Controller — Wire New Fields Through TradeController

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/controllers/TradeController.java`

- [ ] **Step 1: Update TradeController**

Replace the entire `TradeController.java` with:

```java
package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.persistence.TradeEntity;
import com.austinharlan.trading_dashboard.service.TradeService;
import com.austinharlan.trading_dashboard.trades.ClosedTrade;
import com.austinharlan.tradingdashboard.api.TradesApi;
import com.austinharlan.tradingdashboard.dto.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TradeController implements TradesApi {

  private final TradeService tradeService;

  public TradeController(TradeService tradeService) {
    this.tradeService = tradeService;
  }

  @Override
  public ResponseEntity<Trade> logTrade(@Valid LogTradeRequest req) {
    TradeEntity entity =
        tradeService.logTrade(
            req.getTicker().toUpperCase().strip(),
            req.getSide().getValue(),
            req.getQuantity() != null ? BigDecimal.valueOf(req.getQuantity()) : null,
            req.getPricePerShare() != null ? BigDecimal.valueOf(req.getPricePerShare()) : null,
            req.getTradeDate(),
            req.getNotes(),
            req.getAssetType() != null ? req.getAssetType().getValue() : null,
            req.getOptionType() != null ? req.getOptionType().getValue() : null,
            req.getStrikePrice() != null ? BigDecimal.valueOf(req.getStrikePrice()) : null,
            req.getExpirationDate());
    return ResponseEntity.status(201).body(toDto(entity));
  }

  @Override
  public ResponseEntity<TradeListResponse> listTrades(
      String ticker, String side, LocalDate from, LocalDate to) {
    String normalizedTicker = ticker != null ? ticker.toUpperCase().strip() : null;
    List<TradeEntity> trades = tradeService.listTrades(normalizedTicker, side, from, to);
    TradeListResponse response =
        new TradeListResponse().trades(trades.stream().map(this::toDto).toList());
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<Trade> getTrade(Long id) {
    return ResponseEntity.ok(toDto(tradeService.getTrade(id)));
  }

  @Override
  public ResponseEntity<Void> deleteTrade(Long id) {
    tradeService.deleteTrade(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<TradeStats> getTradeStats() {
    TradeService.TradeStats stats = tradeService.getStats();
    TradeStats dto =
        new TradeStats()
            .totalTrades(stats.totalTrades())
            .wins(stats.wins())
            .losses(stats.losses())
            .winRate(stats.winRate())
            .totalPnl(stats.totalPnl().doubleValue())
            .currentStreak(stats.currentStreak())
            .currentStreakType(
                TradeStats.CurrentStreakTypeEnum.fromValue(stats.currentStreakType()))
            .bestWinStreak(stats.bestWinStreak())
            .bestLossStreak(stats.bestLossStreak())
            .avgHoldDays(stats.avgHoldDays())
            .topTickers(
                stats.topTickers().stream()
                    .map(
                        tp ->
                            new TickerPnl()
                                .ticker(tp.ticker())
                                .pnl(tp.pnl().doubleValue())
                                .tradeCount(tp.tradeCount()))
                    .toList());
    return ResponseEntity.ok(dto);
  }

  @Override
  public ResponseEntity<ClosedTradeListResponse> listClosedTrades() {
    List<ClosedTrade> closed = tradeService.getClosedTrades();
    ClosedTradeListResponse response =
        new ClosedTradeListResponse()
            .closedTrades(closed.stream().map(this::toClosedDto).toList());
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<PnlHistoryResponse> getPnlHistory() {
    List<TradeService.PnlHistoryEntry> entries = tradeService.getPnlHistory();
    PnlHistoryResponse response =
        new PnlHistoryResponse()
            .entries(
                entries.stream()
                    .map(
                        e ->
                            new com.austinharlan.tradingdashboard.dto.PnlHistoryEntry()
                                .date(e.date())
                                .pnl(e.pnl().doubleValue())
                                .cumulativePnl(e.cumulativePnl().doubleValue()))
                    .toList());
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<TradeCalendarResponse> getTradeCalendar() {
    List<TradeService.TradeCalendarEntry> entries = tradeService.getTradeCalendar();
    TradeCalendarResponse response =
        new TradeCalendarResponse()
            .entries(
                entries.stream()
                    .map(
                        e ->
                            new com.austinharlan.tradingdashboard.dto.TradeCalendarEntry()
                                .date(e.date())
                                .pnl(e.pnl().doubleValue())
                                .tradeCount(e.tradeCount()))
                    .toList());
    return ResponseEntity.ok(response);
  }

  private Trade toDto(TradeEntity e) {
    Trade dto =
        new Trade()
            .id(e.getId())
            .ticker(e.getTicker())
            .side(Trade.SideEnum.fromValue(e.getSide()))
            .quantity(e.getQuantity().doubleValue())
            .pricePerShare(e.getPricePerShare().doubleValue())
            .tradeDate(e.getTradeDate())
            .notes(e.getNotes())
            .createdAt(OffsetDateTime.ofInstant(e.getCreatedAt(), ZoneOffset.UTC))
            .assetType(Trade.AssetTypeEnum.fromValue(e.getAssetType()))
            .multiplier(e.getMultiplier());
    if (e.getOptionType() != null) {
      dto.optionType(Trade.OptionTypeEnum.fromValue(e.getOptionType()));
    }
    if (e.getStrikePrice() != null) {
      dto.strikePrice(e.getStrikePrice().doubleValue());
    }
    if (e.getExpirationDate() != null) {
      dto.expirationDate(e.getExpirationDate());
    }
    if (e.getLinkedTradeId() != null) {
      dto.linkedTradeId(e.getLinkedTradeId());
    }
    return dto;
  }

  private com.austinharlan.tradingdashboard.dto.ClosedTrade toClosedDto(ClosedTrade ct) {
    var dto =
        new com.austinharlan.tradingdashboard.dto.ClosedTrade()
            .ticker(ct.ticker())
            .quantity(ct.quantity().doubleValue())
            .buyPrice(ct.buyPrice().doubleValue())
            .sellPrice(ct.sellPrice().doubleValue())
            .buyDate(ct.buyDate())
            .sellDate(ct.sellDate())
            .pnl(ct.pnl().doubleValue())
            .pnlPercent(ct.pnlPercent().doubleValue())
            .holdDays((int) ct.holdDays())
            .assetType(
                com.austinharlan.tradingdashboard.dto.ClosedTrade.AssetTypeEnum.fromValue(
                    ct.assetType()));
    if (ct.optionType() != null) {
      dto.optionType(
          com.austinharlan.tradingdashboard.dto.ClosedTrade.OptionTypeEnum.fromValue(
              ct.optionType()));
    }
    if (ct.strikePrice() != null) {
      dto.strikePrice(ct.strikePrice().doubleValue());
    }
    if (ct.expirationDate() != null) {
      dto.expirationDate(ct.expirationDate());
    }
    return dto;
  }
}
```

- [ ] **Step 2: Run spotless and compile**

```bash
cd apps/api/trader-assistant/trading-dashboard && ./gradlew spotlessApply && ./gradlew compileJava
```

Expected: Compiles successfully. If there are generated DTO method name mismatches, check the generated code under `build/generated/openapi/` and adjust accessor names.

- [ ] **Step 3: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/controllers/TradeController.java
git commit -m "feat: wire option fields through TradeController"
```

---

### Task 6: Unit Tests — Bidirectional Matcher and Options P&L

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/test/java/com/austinharlan/trading_dashboard/service/DefaultTradeServiceTest.java`

- [ ] **Step 1: Update test helper and add option trade tests**

Add the following to `DefaultTradeServiceTest.java`. Keep all existing tests (they should still pass since equity trades are unchanged). Add a new helper method and new test methods **after** the existing tests:

After the existing `trade(...)` helper (line ~54), add a new helper:

```java
  private TradeEntity optionTrade(
      String ticker, String side, double qty, double price, String date,
      String optionType, double strike, String expDate) {
    return new TradeEntity(
        USER_ID, ticker, side, BigDecimal.valueOf(qty), BigDecimal.valueOf(price),
        LocalDate.parse(date), null,
        "OPTION", optionType, BigDecimal.valueOf(strike), LocalDate.parse(expDate), 100);
  }
```

Then add these test methods:

```java
  @Test
  void option_longCallBuySell_pnlIncludesMultiplier() {
    when(repository.findAllChronologicalByUserId(USER_ID))
        .thenReturn(
            List.of(
                optionTrade("AAPL", "BUY", 1, 5.00, "2026-01-01", "CALL", 200, "2026-04-18"),
                optionTrade("AAPL", "SELL", 1, 8.00, "2026-02-01", "CALL", 200, "2026-04-18")));
    List<ClosedTrade> closed = service.getClosedTrades();
    assertThat(closed).hasSize(1);
    ClosedTrade ct = closed.get(0);
    assertThat(ct.assetType()).isEqualTo("OPTION");
    assertThat(ct.optionType()).isEqualTo("CALL");
    assertThat(ct.pnl()).isEqualByComparingTo("300.00"); // (8-5) * 1 * 100
  }

  @Test
  void option_shortCallBuyBack_pnlPositive() {
    when(repository.findAllChronologicalByUserId(USER_ID))
        .thenReturn(
            List.of(
                optionTrade("AAPL", "SELL", 1, 4.50, "2026-01-01", "CALL", 210, "2026-05-16"),
                optionTrade("AAPL", "BUY", 1, 2.00, "2026-02-01", "CALL", 210, "2026-05-16")));
    List<ClosedTrade> closed = service.getClosedTrades();
    assertThat(closed).hasSize(1);
    // Short: sell at 4.50, buy at 2.00 → pnl = (4.50 - 2.00) * 1 * 100 = 250
    assertThat(closed.get(0).pnl()).isEqualByComparingTo("250.00");
  }

  @Test
  void option_expireLong_fullLoss() {
    when(repository.findAllChronologicalByUserId(USER_ID))
        .thenReturn(
            List.of(
                optionTrade("TSLA", "BUY", 2, 3.25, "2026-01-01", "PUT", 220, "2026-03-21"),
                optionTrade("TSLA", "EXPIRE", 2, 0, "2026-03-21", "PUT", 220, "2026-03-21")));
    List<ClosedTrade> closed = service.getClosedTrades();
    assertThat(closed).hasSize(1);
    // Long expire: buy 3.25, sell 0 → (0 - 3.25) * 2 * 100 = -650
    assertThat(closed.get(0).pnl()).isEqualByComparingTo("-650.00");
  }

  @Test
  void option_expireShort_keepPremium() {
    when(repository.findAllChronologicalByUserId(USER_ID))
        .thenReturn(
            List.of(
                optionTrade("AAPL", "SELL", 1, 4.50, "2026-01-01", "CALL", 210, "2026-05-16"),
                optionTrade("AAPL", "EXPIRE", 1, 0, "2026-05-16", "CALL", 210, "2026-05-16")));
    List<ClosedTrade> closed = service.getClosedTrades();
    assertThat(closed).hasSize(1);
    // Short expire: sell 4.50, buy 0 → (4.50 - 0) * 1 * 100 = 450
    assertThat(closed.get(0).pnl()).isEqualByComparingTo("450.00");
  }

  @Test
  void option_differentContracts_matchedIndependently() {
    when(repository.findAllChronologicalByUserId(USER_ID))
        .thenReturn(
            List.of(
                optionTrade("AAPL", "BUY", 1, 5.00, "2026-01-01", "CALL", 200, "2026-04-18"),
                optionTrade("AAPL", "BUY", 1, 3.00, "2026-01-01", "CALL", 210, "2026-04-18"),
                optionTrade("AAPL", "SELL", 1, 8.00, "2026-02-01", "CALL", 200, "2026-04-18"),
                optionTrade("AAPL", "SELL", 1, 4.00, "2026-02-01", "CALL", 210, "2026-04-18")));
    List<ClosedTrade> closed = service.getClosedTrades();
    assertThat(closed).hasSize(2);
    // $200 strike: (8-5)*100 = 300
    ClosedTrade s200 =
        closed.stream().filter(c -> c.strikePrice().doubleValue() == 200).findFirst().orElseThrow();
    assertThat(s200.pnl()).isEqualByComparingTo("300.00");
    // $210 strike: (4-3)*100 = 100
    ClosedTrade s210 =
        closed.stream().filter(c -> c.strikePrice().doubleValue() == 210).findFirst().orElseThrow();
    assertThat(s210.pnl()).isEqualByComparingTo("100.00");
  }

  @Test
  void equity_existingTests_stillMatchByTicker() {
    // Equity trades should still be grouped by ticker only (null option fields)
    when(repository.findAllChronologicalByUserId(USER_ID))
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 10, 150.00, "2026-01-01"),
                trade("AAPL", "SELL", 10, 200.00, "2026-02-01")));
    List<ClosedTrade> closed = service.getClosedTrades();
    assertThat(closed).hasSize(1);
    assertThat(closed.get(0).assetType()).isEqualTo("EQUITY");
    assertThat(closed.get(0).pnl()).isEqualByComparingTo("500.00");
  }
```

- [ ] **Step 2: Run tests**

```bash
cd apps/api/trader-assistant/trading-dashboard && ./gradlew spotlessApply && ./gradlew test --tests "*.DefaultTradeServiceTest"
```

Expected: All existing tests PASS. All new option tests PASS.

- [ ] **Step 3: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/src/test/java/com/austinharlan/trading_dashboard/service/DefaultTradeServiceTest.java
git commit -m "test: add option trade unit tests for bidirectional matcher"
```

---

### Task 7: Demo Seed Data — Add Sample Option Trades

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/service/DemoService.java`

- [ ] **Step 1: Add option trade helper and seed data**

In `DemoService.java`, add a new helper method after the existing `trade(...)` method (line ~120):

```java
  private void optionTrade(
      Long userId,
      String date,
      String ticker,
      String side,
      String qty,
      String price,
      String notes,
      String optionType,
      String strike,
      String expDate) {
    tradeRepository.save(
        new TradeEntity(
            userId,
            ticker,
            side,
            new BigDecimal(qty),
            new BigDecimal(price),
            LocalDate.parse(date),
            notes,
            "OPTION",
            optionType,
            new BigDecimal(strike),
            LocalDate.parse(expDate),
            100));
  }
```

Then add option trade calls at the end of `seedTrades()`, after the AMD sell (line ~100):

```java
    // Options trades
    optionTrade(userId, "2026-02-10", "AAPL", "SELL", "1", "6.20",
        "Covered call against AAPL shares", "CALL", "200", "2026-04-18");
    optionTrade(userId, "2026-03-05", "AAPL", "BUY", "1", "3.50",
        "Bought back covered call on dip, +$270", "CALL", "200", "2026-04-18");
    optionTrade(userId, "2026-01-15", "NVDA", "BUY", "1", "4.80",
        "Bullish call on AI thesis", "CALL", "130", "2026-03-21");
    optionTrade(userId, "2026-02-20", "NVDA", "SELL", "1", "8.50",
        "Took profit on NVDA call, +$370", "CALL", "130", "2026-03-21");
    optionTrade(userId, "2026-02-05", "TSLA", "BUY", "1", "3.25",
        "Hedge against TSLA downturn", "PUT", "220", "2026-03-21");
    optionTrade(userId, "2026-03-21", "TSLA", "EXPIRE", "1", "0",
        "TSLA put expired worthless, -$325", "PUT", "220", "2026-03-21");
```

- [ ] **Step 2: Run spotless**

```bash
cd apps/api/trader-assistant/trading-dashboard && ./gradlew spotlessApply
```

- [ ] **Step 3: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/service/DemoService.java
git commit -m "feat: add sample option trades to demo seed data"
```

---

### Task 8: Integration Tests — Options API End-to-End

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/test/java/com/austinharlan/trading_dashboard/TradeIT.java`

- [ ] **Step 1: Add option trade integration tests**

Add the following test methods to `TradeIT.java` after the existing tests:

```java
  @Test
  void postOptionTrade_returns201WithOptionFields() {
    String body =
        """
        {"ticker":"AAPL","side":"BUY","quantity":1,"pricePerShare":5.00,
         "tradeDate":"2026-03-01","assetType":"OPTION","optionType":"CALL",
         "strikePrice":200.0,"expirationDate":"2026-04-18"}
        """;

    ResponseEntity<Map> response =
        rest.exchange("/api/trades", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsEntry("assetType", "OPTION");
    assertThat(response.getBody()).containsEntry("optionType", "CALL");
    assertThat(((Number) response.getBody().get("strikePrice")).doubleValue())
        .isCloseTo(200.0, within(0.01));
    assertThat(((Number) response.getBody().get("multiplier")).intValue()).isEqualTo(100);
  }

  @Test
  void closedOptionTrades_includesMultiplierInPnl() {
    // BUY 1 AAPL $200 CALL @ $5
    tradeRepository.save(
        new TradeEntity(
            testUserId, "AAPL", "BUY", BigDecimal.ONE, BigDecimal.valueOf(5),
            LocalDate.of(2026, 1, 1), null,
            "OPTION", "CALL", BigDecimal.valueOf(200), LocalDate.of(2026, 4, 18), 100));
    // SELL 1 AAPL $200 CALL @ $8
    tradeRepository.save(
        new TradeEntity(
            testUserId, "AAPL", "SELL", BigDecimal.ONE, BigDecimal.valueOf(8),
            LocalDate.of(2026, 2, 1), null,
            "OPTION", "CALL", BigDecimal.valueOf(200), LocalDate.of(2026, 4, 18), 100));

    ResponseEntity<Map> response =
        rest.exchange("/api/trades/closed", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<Map> closed = (List<Map>) response.getBody().get("closedTrades");
    assertThat(closed).hasSize(1);
    assertThat(((Number) closed.get(0).get("pnl")).doubleValue())
        .isCloseTo(300.0, within(0.01)); // (8-5) * 1 * 100
    assertThat(closed.get(0).get("assetType")).isEqualTo("OPTION");
  }

  @Test
  void equityTrades_stillWorkUnchanged() {
    // Existing equity flow should be backward compatible
    String body =
        """
        {"ticker":"MSFT","side":"BUY","quantity":5,"pricePerShare":400.0,"tradeDate":"2026-03-01"}
        """;

    ResponseEntity<Map> response =
        rest.exchange("/api/trades", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsEntry("assetType", "EQUITY");
    assertThat(((Number) response.getBody().get("multiplier")).intValue()).isEqualTo(1);
  }

  @Test
  void postOptionTrade_rejectsExpireOnEquity() {
    String body =
        """
        {"ticker":"AAPL","side":"EXPIRE","assetType":"EQUITY"}
        """;

    ResponseEntity<Map> response =
        rest.exchange("/api/trades", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
```

- [ ] **Step 2: Run all tests**

```bash
cd apps/api/trader-assistant/trading-dashboard && ./gradlew spotlessApply && ./gradlew test
```

Expected: All existing and new tests pass. If integration tests are skipped (no Docker), verify with `./gradlew test --info | grep -i skip` that only IT tests are skipped.

- [ ] **Step 3: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/src/test/java/com/austinharlan/trading_dashboard/TradeIT.java
git commit -m "test: add options trade integration tests"
```

---

### Task 9: Frontend — Log Trade Modal Options Support

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html`

- [ ] **Step 1: Update the Log Trade modal HTML**

Replace the Log Trade modal (lines ~1223-1262) with the updated version that includes asset type toggle, option fields, and contextual labels:

```html
<div class="modal-backdrop" id="logTradeModal">
  <div class="modal">
    <h3>Log Trade</h3>
    <div class="field-group">
      <div>
        <div class="field-label">Asset Type</div>
        <div style="display:flex;gap:4px;">
          <button class="btn btn-sm lt-asset-btn active" data-asset="EQUITY" onclick="setAssetType('EQUITY')" style="flex:1">EQUITY</button>
          <button class="btn btn-sm lt-asset-btn" data-asset="OPTION" onclick="setAssetType('OPTION')" style="flex:1">OPTION</button>
        </div>
      </div>
      <div>
        <div class="field-label">Ticker</div>
        <input class="field" id="lt-ticker" type="text" placeholder="e.g. AAPL" autocomplete="off" style="text-transform:uppercase">
      </div>
      <div>
        <div class="field-label">Side</div>
        <div style="display:flex;gap:4px;">
          <button class="btn btn-sm lt-side-btn active" data-side="BUY" onclick="setTradeSide('BUY')" style="flex:1">BUY</button>
          <button class="btn btn-sm lt-side-btn" data-side="SELL" onclick="setTradeSide('SELL')" style="flex:1">SELL</button>
        </div>
        <div id="lt-resolve-row" style="display:none;margin-top:4px;">
          <div class="field-label" style="font-size:9px;color:var(--text-muted);margin-bottom:2px;">Resolve</div>
          <div style="display:flex;gap:4px;">
            <button class="btn btn-sm lt-side-btn" data-side="EXPIRE" onclick="setTradeSide('EXPIRE')" style="flex:1;opacity:.7;font-size:9px">EXPIRE</button>
            <button class="btn btn-sm lt-side-btn" data-side="EXERCISE" onclick="setTradeSide('EXERCISE')" style="flex:1;opacity:.7;font-size:9px">EXERCISE</button>
          </div>
        </div>
      </div>
      <div id="lt-option-fields" style="display:none">
        <div class="field-label">Type</div>
        <div style="display:flex;gap:4px;margin-bottom:8px;">
          <button class="btn btn-sm lt-otype-btn active" data-otype="CALL" onclick="setOptionType('CALL')" style="flex:1">CALL</button>
          <button class="btn btn-sm lt-otype-btn" data-otype="PUT" onclick="setOptionType('PUT')" style="flex:1">PUT</button>
        </div>
        <div class="field-label">Strike ($)</div>
        <input class="field" id="lt-strike" type="number" placeholder="200.00" min="0.0001" step="any" style="margin-bottom:8px;">
        <div class="field-label">Expiration</div>
        <input class="field" id="lt-expiration" type="date">
      </div>
      <div id="lt-qty-wrap">
        <div class="field-label" id="lt-qty-label">Quantity</div>
        <input class="field" id="lt-qty" type="number" placeholder="10" min="0.0001" step="any">
      </div>
      <div id="lt-price-wrap">
        <div class="field-label" id="lt-price-label">Price / Share ($)</div>
        <input class="field" id="lt-price" type="number" placeholder="163.42" min="0.0001" step="any">
        <div id="lt-total-hint" style="display:none;font-size:9px;color:var(--text-muted);margin-top:2px;"></div>
      </div>
      <div>
        <div class="field-label">Date</div>
        <input class="field" id="lt-date" type="date">
      </div>
      <div>
        <div class="field-label">Notes (optional)</div>
        <textarea class="field" id="lt-notes" rows="2" placeholder="Bought the dip..."></textarea>
      </div>
    </div>
    <div class="modal-err" id="lt-err"></div>
    <div class="modal-actions" id="lt-actions">
      <button class="btn btn-sm" onclick="closeLogTrade()">Cancel</button>
      <button class="btn btn-primary btn-sm" id="lt-submit" onclick="submitLogTrade()">Log Trade</button>
    </div>
    <div id="lt-link-prompt" style="display:none"></div>
  </div>
</div>
```

- [ ] **Step 2: Add JS functions for asset type, option type, and total hint**

In the `<script>` section, add these functions near the existing `setTradeSide` function (~line 2296):

```javascript
  let _tradeAssetType = 'EQUITY';
  let _tradeOptionType = 'CALL';

  function setAssetType(type) {
    _tradeAssetType = type;
    document.querySelectorAll('.lt-asset-btn').forEach(b => {
      b.classList.toggle('active', b.dataset.asset === type);
    });
    const isOption = type === 'OPTION';
    document.getElementById('lt-option-fields').style.display = isOption ? '' : 'none';
    document.getElementById('lt-resolve-row').style.display = isOption ? '' : 'none';
    document.getElementById('lt-qty-label').textContent = isOption ? 'Contracts' : 'Quantity';
    document.getElementById('lt-price-label').textContent = isOption ? 'Premium ($)' : 'Price / Share ($)';
    updateTotalHint();
    updateFieldVisibility();
  }

  function setOptionType(type) {
    _tradeOptionType = type;
    document.querySelectorAll('.lt-otype-btn').forEach(b => {
      b.classList.toggle('active', b.dataset.otype === type);
    });
  }

  function updateFieldVisibility() {
    const side = _tradeSide;
    const isOption = _tradeAssetType === 'OPTION';
    const isExpire = side === 'EXPIRE';
    const isExercise = side === 'EXERCISE';
    document.getElementById('lt-qty-wrap').style.display = isExpire ? 'none' : '';
    document.getElementById('lt-price-wrap').style.display = (isExpire || isExercise) ? 'none' : '';
  }

  function updateTotalHint() {
    const hint = document.getElementById('lt-total-hint');
    if (_tradeAssetType !== 'OPTION' || ['EXPIRE', 'EXERCISE'].includes(_tradeSide)) {
      hint.style.display = 'none';
      return;
    }
    const qty = parseFloat(document.getElementById('lt-qty').value) || 0;
    const price = parseFloat(document.getElementById('lt-price').value) || 0;
    if (qty > 0 && price > 0) {
      const total = qty * price * 100;
      hint.textContent = `${qty} × $${price.toFixed(2)} × 100 = $${total.toLocaleString('en-US', {minimumFractionDigits: 2})}`;
      hint.style.display = '';
    } else {
      hint.style.display = 'none';
    }
  }
```

- [ ] **Step 3: Update existing `setTradeSide` to call `updateFieldVisibility`**

Replace the existing `setTradeSide` function with:

```javascript
  function setTradeSide(side) {
    _tradeSide = side;
    document.querySelectorAll('.lt-side-btn').forEach(b => {
      b.classList.toggle('active', b.dataset.side === side);
    });
    updateFieldVisibility();
    updateTotalHint();
  }
```

- [ ] **Step 4: Add input listeners for total hint updates**

After the `updateTotalHint` function, add:

```javascript
  document.getElementById('lt-qty').addEventListener('input', updateTotalHint);
  document.getElementById('lt-price').addEventListener('input', updateTotalHint);
```

- [ ] **Step 5: Update `openLogTrade` to reset option state**

Replace the existing `openLogTrade` function with:

```javascript
  function openLogTrade() {
    _tradeSide = 'BUY';
    _tradeAssetType = 'EQUITY';
    _tradeOptionType = 'CALL';
    document.getElementById('lt-ticker').value = '';
    document.getElementById('lt-qty').value = '';
    document.getElementById('lt-price').value = '';
    document.getElementById('lt-date').value = new Date().toISOString().slice(0, 10);
    document.getElementById('lt-notes').value = '';
    document.getElementById('lt-strike').value = '';
    document.getElementById('lt-expiration').value = '';
    document.getElementById('lt-err').textContent = '';
    document.getElementById('lt-link-prompt').style.display = 'none';
    document.getElementById('lt-actions').style.display = '';
    document.getElementById('lt-total-hint').style.display = 'none';
    setAssetType('EQUITY');
    setTradeSide('BUY');
    setOptionType('CALL');
    document.getElementById('logTradeModal').classList.add('open');
    document.getElementById('lt-ticker').focus();
  }
```

- [ ] **Step 6: Update `submitLogTrade` to include option fields**

Replace the existing `submitLogTrade` function with:

```javascript
  async function submitLogTrade() {
    const ticker = document.getElementById('lt-ticker').value.trim().toUpperCase();
    const qty = parseFloat(document.getElementById('lt-qty').value);
    const price = parseFloat(document.getElementById('lt-price').value);
    const date = document.getElementById('lt-date').value;
    const notes = document.getElementById('lt-notes').value.trim() || null;
    const errEl = document.getElementById('lt-err');
    const btn = document.getElementById('lt-submit');
    const isOption = _tradeAssetType === 'OPTION';
    const isExpire = _tradeSide === 'EXPIRE';
    const isExercise = _tradeSide === 'EXERCISE';

    if (!ticker) { errEl.textContent = 'Ticker is required.'; return; }
    if (!isExpire && !(qty > 0)) { errEl.textContent = (isOption ? 'Contracts' : 'Quantity') + ' must be greater than 0.'; return; }
    if (!isExpire && !isExercise && !(price > 0)) { errEl.textContent = (isOption ? 'Premium' : 'Price') + ' must be greater than 0.'; return; }
    if (!date) { errEl.textContent = 'Date is required.'; return; }

    if (isOption) {
      const strike = parseFloat(document.getElementById('lt-strike').value);
      const exp = document.getElementById('lt-expiration').value;
      if (!(strike > 0)) { errEl.textContent = 'Strike price is required.'; return; }
      if (!exp) { errEl.textContent = 'Expiration date is required.'; return; }
    }

    btn.disabled = true;
    errEl.textContent = '';

    const body = { ticker, side: _tradeSide, tradeDate: date, notes };
    if (!isExpire) body.quantity = qty;
    if (!isExpire && !isExercise) body.pricePerShare = price;
    if (isExercise) body.quantity = qty;

    if (isOption) {
      body.assetType = 'OPTION';
      body.optionType = _tradeOptionType;
      body.strikePrice = parseFloat(document.getElementById('lt-strike').value);
      body.expirationDate = document.getElementById('lt-expiration').value;
    }

    try {
      const r = await fetch('/api/trades', {
        method: 'POST',
        headers: { 'X-API-KEY': KEY, 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (!r.ok) {
        const respBody = await r.json().catch(() => ({}));
        errEl.textContent = respBody.message || 'Failed to log trade.';
        return;
      }

      if (!isOption) {
        const posRes = await get('/api/portfolio/positions').catch(() => null);
        const positions = posRes?.positions || [];
        const existing = positions.find(p => p.ticker === ticker);
        if (existing) {
          showPositionLinkPrompt(ticker, _tradeSide, qty, price, existing);
          return;
        }
      }

      closeLogTrade();
      loadTrades();
    } catch (err) {
      console.error('Log trade error', err);
      errEl.textContent = 'Network error.';
    } finally {
      btn.disabled = false;
    }
  }
```

- [ ] **Step 7: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html
git commit -m "feat: add options fields to Log Trade modal with asset type toggle"
```

---

### Task 10: Frontend — Closed Trades Table with Option Sub-lines

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html`

- [ ] **Step 1: Update the closed trades table rendering**

In the `loadTrades` function (around lines ~2187-2205), replace the closed trades table rendering with:

```javascript
      if (closed.length === 0) {
        html += `<div class="state-box" style="padding:24px 0;font-size:10px;color:var(--text-muted);">No closed trades yet — log both a buy and sell for the same ticker.</div>`;
      } else {
        html += `<div class="table-wrap"><table class="data-table">
          <thead><tr>
            <th>Ticker</th><th>Qty</th><th>Buy</th><th>Sell</th><th>P&L</th><th>Held</th>
          </tr></thead>
          <tbody>
            ${closed.map(ct => {
              const cls = ct.pnl >= 0 ? 'td-pos' : 'td-neg';
              const sign = ct.pnl >= 0 ? '+' : '';
              const isOption = ct.assetType === 'OPTION';
              const buyDisplay = ct.buyPrice === 0 ? (ct.sellDate ? '$0 EXP' : '$0') : money(ct.buyPrice);
              const sellDisplay = ct.sellPrice === 0 ? '$0 EXP' : money(ct.sellPrice);
              const qtyLabel = isOption ? num(ct.quantity) + 'c' : num(ct.quantity);
              let optionLine = '';
              if (isOption) {
                const typeChar = ct.optionType === 'CALL' ? 'Call' : 'Put';
                const expStr = ct.expirationDate ? new Date(ct.expirationDate + 'T00:00:00').toLocaleDateString('en-US', {month:'numeric',day:'numeric',year:'2-digit'}) : '';
                optionLine = `<tr><td colspan="6" style="padding:0 0 6px 8px;border:none;font-size:9px;color:var(--text-muted);">$${ct.strikePrice} ${typeChar} ${expStr}</td></tr>`;
              }
              return `<tr>
                <td class="${cls}"><strong>${ct.ticker}</strong></td>
                <td>${qtyLabel}</td>
                <td>${buyDisplay}</td>
                <td>${sellDisplay}</td>
                <td class="${cls}">${sign}${money(ct.pnl)}</td>
                <td>${ct.holdDays}d</td>
              </tr>${optionLine}`;
            }).join('')}
          </tbody>
        </table></div>`;
      }
```

- [ ] **Step 2: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html
git commit -m "feat: show option contract details as sub-lines in closed trades table"
```

---

### Task 11: Full Build Verification

- [ ] **Step 1: Run full CI build**

```bash
cd apps/api/trader-assistant/trading-dashboard && ./gradlew spotlessCheck build --no-daemon
```

Expected: BUILD SUCCESSFUL. All tests pass, no spotless violations.

- [ ] **Step 2: Run app locally and verify**

```bash
cd apps/api/trader-assistant/trading-dashboard && ./gradlew bootRun
```

Open https://localhost:8080 and verify:
- Log Trade modal has EQUITY/OPTION toggle
- Selecting OPTION shows Type, Strike, Expiration fields
- Side row gains Resolve row with EXPIRE/EXERCISE
- Quantity label changes to "Contracts" for options
- Premium label + total hint appear for options
- Existing equity trade flow still works unchanged

- [ ] **Step 3: Commit any final fixes**

```bash
cd apps/api/trader-assistant/trading-dashboard && ./gradlew spotlessApply
git add -A
git commit -m "fix: address any remaining issues from full build verification"
```

(Only commit if there were actual fixes needed.)
