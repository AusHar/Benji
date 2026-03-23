# Trade Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add manual trade logging with FIFO matching and performance analytics to the Portfolio page, behind a Positions | Trades tab bar.

**Architecture:** Trades are independent entities stored in a `trades` table. FIFO matching computes closed trades at read time in the service layer. The frontend adds a tab bar to the Portfolio page; the Trades tab renders stats, charts, and a closed trades table. Logging a trade for a held ticker optionally updates the position via a frontend prompt.

**Tech Stack:** Spring Boot, JPA/Hibernate, Flyway, PostgreSQL, OpenAPI codegen, single-file SPA (vanilla JS/HTML/CSS)

**Spec:** `docs/superpowers/specs/2026-03-23-trade-tracking-design.md`

---

### Task 1: Flyway Migration

**Files:**
- Create: `src/main/resources/db/migration/V4__trades.sql`

- [ ] **Step 1: Create the migration file**

```sql
CREATE TABLE trades (
    id              BIGSERIAL       PRIMARY KEY,
    ticker          VARCHAR(12)     NOT NULL,
    side            VARCHAR(4)      NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity        DECIMAL(12,4)   NOT NULL CHECK (quantity > 0),
    price_per_share DECIMAL(12,4)   NOT NULL CHECK (price_per_share > 0),
    trade_date      DATE            NOT NULL DEFAULT CURRENT_DATE,
    notes           TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trades_ticker ON trades (ticker);
CREATE INDEX idx_trades_trade_date ON trades (trade_date);
```

- [ ] **Step 2: Verify migration applies**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew test --tests "*.PortfolioPositionRepositoryIT.flywayMigrationsRunOnStartup" --no-daemon 2>&1 | tail -5`
Expected: PASS (Flyway runs all migrations including V4 on Testcontainers startup)

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V4__trades.sql
git commit -m "feat: add V4 Flyway migration for trades table"
```

---

### Task 2: OpenAPI Spec — Trade Endpoints and Schemas

**Files:**
- Modify: `openAPI.yaml`

- [ ] **Step 1: Add Trade schemas to `components/schemas`**

Add the following schemas at the end of the `components/schemas` section in `openAPI.yaml`:

```yaml
    LogTradeRequest:
      type: object
      required: [ticker, side, quantity, pricePerShare]
      properties:
        ticker:
          type: string
          minLength: 1
          maxLength: 12
        side:
          type: string
          enum: [BUY, SELL]
        quantity:
          type: number
          format: double
          minimum: 0.0001
        pricePerShare:
          type: number
          format: double
          minimum: 0.0001
        tradeDate:
          type: string
          format: date
        notes:
          type: string

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
          enum: [BUY, SELL]
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

    TradeListResponse:
      type: object
      properties:
        trades:
          type: array
          items:
            $ref: '#/components/schemas/Trade'

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

    ClosedTradeListResponse:
      type: object
      properties:
        closedTrades:
          type: array
          items:
            $ref: '#/components/schemas/ClosedTrade'

    TickerPnl:
      type: object
      properties:
        ticker:
          type: string
        pnl:
          type: number
          format: double
        tradeCount:
          type: integer

    TradeStats:
      type: object
      properties:
        totalTrades:
          type: integer
        wins:
          type: integer
        losses:
          type: integer
        winRate:
          type: number
          format: double
        totalPnl:
          type: number
          format: double
        currentStreak:
          type: integer
        currentStreakType:
          type: string
          enum: [WIN, LOSS, NONE]
        bestWinStreak:
          type: integer
        bestLossStreak:
          type: integer
        avgHoldDays:
          type: number
          format: double
        topTickers:
          type: array
          items:
            $ref: '#/components/schemas/TickerPnl'

    PnlHistoryEntry:
      type: object
      properties:
        date:
          type: string
          format: date
        pnl:
          type: number
          format: double
        cumulativePnl:
          type: number
          format: double

    PnlHistoryResponse:
      type: object
      properties:
        entries:
          type: array
          items:
            $ref: '#/components/schemas/PnlHistoryEntry'

    TradeCalendarEntry:
      type: object
      properties:
        date:
          type: string
          format: date
        pnl:
          type: number
          format: double
        tradeCount:
          type: integer

    TradeCalendarResponse:
      type: object
      properties:
        entries:
          type: array
          items:
            $ref: '#/components/schemas/TradeCalendarEntry'
```

- [ ] **Step 2: Add Trade paths**

Add the following paths to the `paths` section in `openAPI.yaml`:

```yaml
  /api/trades:
    get:
      tags:
        - Trades
      operationId: listTrades
      summary: List all trades with optional filters.
      parameters:
        - name: ticker
          in: query
          required: false
          schema:
            type: string
        - name: side
          in: query
          required: false
          schema:
            type: string
            enum: [BUY, SELL]
        - name: from
          in: query
          required: false
          schema:
            type: string
            format: date
        - name: to
          in: query
          required: false
          schema:
            type: string
            format: date
      responses:
        '200':
          description: List of trades.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TradeListResponse'
    post:
      tags:
        - Trades
      operationId: logTrade
      summary: Log a new trade.
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/LogTradeRequest'
      responses:
        '201':
          description: Trade logged.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Trade'
        '400':
          description: Invalid request body.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /api/trades/{id}:
    get:
      tags:
        - Trades
      operationId: getTrade
      summary: Get a single trade by ID.
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '200':
          description: Trade details.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Trade'
        '404':
          description: Trade not found.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
    delete:
      tags:
        - Trades
      operationId: deleteTrade
      summary: Delete a trade.
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '204':
          description: Trade deleted.
        '404':
          description: Trade not found.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /api/trades/stats:
    get:
      tags:
        - Trades
      operationId: getTradeStats
      summary: Aggregated trade performance stats.
      responses:
        '200':
          description: Trade stats.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TradeStats'

  /api/trades/closed:
    get:
      tags:
        - Trades
      operationId: listClosedTrades
      summary: FIFO-matched closed trades with P&L.
      responses:
        '200':
          description: Closed trades.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ClosedTradeListResponse'

  /api/trades/pnl-history:
    get:
      tags:
        - Trades
      operationId: getPnlHistory
      summary: Cumulative P&L over time.
      responses:
        '200':
          description: P&L history.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/PnlHistoryResponse'

  /api/trades/calendar:
    get:
      tags:
        - Trades
      operationId: getTradeCalendar
      summary: Daily P&L by date for calendar heatmap.
      responses:
        '200':
          description: Trade calendar.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TradeCalendarResponse'
```

- [ ] **Step 3: Generate DTOs and interfaces**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew openApiGenerate --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL. Verify generated files exist:
Run: `ls build/generated/openapi/src/main/java/com/austinharlan/tradingdashboard/api/TradesApi.java build/generated/openapi/src/main/java/com/austinharlan/tradingdashboard/dto/Trade.java build/generated/openapi/src/main/java/com/austinharlan/tradingdashboard/dto/LogTradeRequest.java`
Expected: All three files exist.

- [ ] **Step 4: Commit**

```bash
git add openAPI.yaml
git commit -m "feat: add trade endpoints and schemas to OpenAPI spec"
```

---

### Task 3: JPA Entity — TradeEntity

**Files:**
- Create: `src/main/java/com/austinharlan/trading_dashboard/persistence/TradeEntity.java`

- [ ] **Step 1: Create the entity**

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

  @Column(name = "ticker", nullable = false, length = 12)
  private String ticker;

  @Column(name = "side", nullable = false, length = 4)
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

  protected TradeEntity() {}

  public TradeEntity(
      String ticker,
      String side,
      BigDecimal quantity,
      BigDecimal pricePerShare,
      LocalDate tradeDate,
      String notes) {
    this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
    this.side = Objects.requireNonNull(side, "side must not be null");
    this.quantity = Objects.requireNonNull(quantity, "quantity must not be null");
    this.pricePerShare = Objects.requireNonNull(pricePerShare, "pricePerShare must not be null");
    this.tradeDate = Objects.requireNonNull(tradeDate, "tradeDate must not be null");
    this.notes = notes;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getTicker() {
    return ticker;
  }

  public String getSide() {
    return side;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BigDecimal getPricePerShare() {
    return pricePerShare;
  }

  public LocalDate getTradeDate() {
    return tradeDate;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
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

- [ ] **Step 2: Verify it compiles**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew compileJava --no-daemon 2>&1 | tail -3`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/austinharlan/trading_dashboard/persistence/TradeEntity.java
git commit -m "feat: add TradeEntity JPA entity"
```

---

### Task 4: Repository — TradeRepository

**Files:**
- Create: `src/main/java/com/austinharlan/trading_dashboard/persistence/TradeRepository.java`

- [ ] **Step 1: Create the repository**

```java
package com.austinharlan.trading_dashboard.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<TradeEntity, Long> {

  List<TradeEntity> findAllByOrderByTradeDateDescCreatedAtDesc();

  @Query(
      """
      select t from TradeEntity t
      where (:ticker is null or t.ticker = :ticker)
        and (:side is null or t.side = :side)
        and (:fromDate is null or t.tradeDate >= :fromDate)
        and (:toDate is null or t.tradeDate <= :toDate)
      order by t.tradeDate desc, t.createdAt desc
      """)
  List<TradeEntity> findFiltered(
      @Param("ticker") String ticker,
      @Param("side") String side,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);

  @Query(
      """
      select t from TradeEntity t
      order by t.tradeDate asc, t.createdAt asc
      """)
  List<TradeEntity> findAllChronological();
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew compileJava --no-daemon 2>&1 | tail -3`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/austinharlan/trading_dashboard/persistence/TradeRepository.java
git commit -m "feat: add TradeRepository with filtered and chronological queries"
```

---

### Task 5: Service Interface and Domain Records

**Files:**
- Create: `src/main/java/com/austinharlan/trading_dashboard/service/TradeService.java`
- Create: `src/main/java/com/austinharlan/trading_dashboard/trades/ClosedTrade.java`

- [ ] **Step 1: Create the ClosedTrade domain record**

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
    long holdDays) {}
```

- [ ] **Step 2: Create the TradeService interface**

```java
package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.persistence.TradeEntity;
import com.austinharlan.trading_dashboard.trades.ClosedTrade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

public interface TradeService {

  TradeEntity logTrade(
      String ticker,
      String side,
      BigDecimal quantity,
      BigDecimal pricePerShare,
      @Nullable LocalDate tradeDate,
      @Nullable String notes);

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

- [ ] **Step 3: Verify it compiles**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew compileJava --no-daemon 2>&1 | tail -3`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/austinharlan/trading_dashboard/trades/ClosedTrade.java \
        src/main/java/com/austinharlan/trading_dashboard/service/TradeService.java
git commit -m "feat: add TradeService interface and ClosedTrade domain record"
```

---

### Task 6: Service Implementation — FIFO Matching (TDD)

**Files:**
- Create: `src/test/java/com/austinharlan/trading_dashboard/service/DefaultTradeServiceTest.java`
- Create: `src/main/java/com/austinharlan/trading_dashboard/service/DefaultTradeService.java`

- [ ] **Step 1: Write failing tests for FIFO matching**

```java
package com.austinharlan.trading_dashboard.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.austinharlan.trading_dashboard.persistence.TradeEntity;
import com.austinharlan.trading_dashboard.persistence.TradeRepository;
import com.austinharlan.trading_dashboard.trades.ClosedTrade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTradeServiceTest {

  private TradeRepository repository;
  private DefaultTradeService service;

  @BeforeEach
  void setUp() {
    repository = mock(TradeRepository.class);
    service = new DefaultTradeService(repository);
  }

  private TradeEntity trade(String ticker, String side, double qty, double price, String date) {
    return new TradeEntity(
        ticker,
        side,
        BigDecimal.valueOf(qty),
        BigDecimal.valueOf(price),
        LocalDate.parse(date),
        null);
  }

  @Test
  void fifo_singleBuyAndSell_producesOneClosedTrade() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 10, 150.00, "2026-01-01"),
                trade("AAPL", "SELL", 10, 200.00, "2026-02-01")));

    List<ClosedTrade> closed = service.getClosedTrades();

    assertThat(closed).hasSize(1);
    ClosedTrade ct = closed.get(0);
    assertThat(ct.ticker()).isEqualTo("AAPL");
    assertThat(ct.quantity()).isEqualByComparingTo("10");
    assertThat(ct.buyPrice()).isEqualByComparingTo("150.00");
    assertThat(ct.sellPrice()).isEqualByComparingTo("200.00");
    assertThat(ct.pnl()).isEqualByComparingTo("500.00");
    assertThat(ct.holdDays()).isEqualTo(31);
  }

  @Test
  void fifo_partialSell_closesOnlyMatchedQuantity() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 100, 150.00, "2026-01-01"),
                trade("AAPL", "SELL", 50, 200.00, "2026-02-01")));

    List<ClosedTrade> closed = service.getClosedTrades();

    assertThat(closed).hasSize(1);
    assertThat(closed.get(0).quantity()).isEqualByComparingTo("50");
    assertThat(closed.get(0).pnl()).isEqualByComparingTo("2500.00");
  }

  @Test
  void fifo_largeSellConsumesMultipleBuys() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 30, 100.00, "2026-01-01"),
                trade("AAPL", "BUY", 20, 120.00, "2026-01-15"),
                trade("AAPL", "SELL", 50, 150.00, "2026-02-01")));

    List<ClosedTrade> closed = service.getClosedTrades();

    assertThat(closed).hasSize(2);
    // First lot: 30 @ 100 sold @ 150 = +1500
    assertThat(closed.get(0).quantity()).isEqualByComparingTo("30");
    assertThat(closed.get(0).buyPrice()).isEqualByComparingTo("100.00");
    assertThat(closed.get(0).pnl()).isEqualByComparingTo("1500.00");
    // Second lot: 20 @ 120 sold @ 150 = +600
    assertThat(closed.get(1).quantity()).isEqualByComparingTo("20");
    assertThat(closed.get(1).buyPrice()).isEqualByComparingTo("120.00");
    assertThat(closed.get(1).pnl()).isEqualByComparingTo("600.00");
  }

  @Test
  void fifo_sellWithNoBuys_producesNoClosedTrade() {
    when(repository.findAllChronological())
        .thenReturn(List.of(trade("AAPL", "SELL", 10, 200.00, "2026-02-01")));

    List<ClosedTrade> closed = service.getClosedTrades();

    assertThat(closed).isEmpty();
  }

  @Test
  void fifo_multipleTickersMatchedIndependently() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 10, 150.00, "2026-01-01"),
                trade("MSFT", "BUY", 5, 300.00, "2026-01-01"),
                trade("AAPL", "SELL", 10, 180.00, "2026-02-01"),
                trade("MSFT", "SELL", 5, 350.00, "2026-02-01")));

    List<ClosedTrade> closed = service.getClosedTrades();

    assertThat(closed).hasSize(2);
    assertThat(closed).extracting(ClosedTrade::ticker).containsExactlyInAnyOrder("AAPL", "MSFT");
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew test --tests "*.DefaultTradeServiceTest" --no-daemon 2>&1 | tail -10`
Expected: FAIL — `DefaultTradeService` class does not exist yet.

- [ ] **Step 3: Implement DefaultTradeService with FIFO matching**

```java
package com.austinharlan.trading_dashboard.service;

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
      BigDecimal quantity,
      BigDecimal pricePerShare,
      @Nullable LocalDate tradeDate,
      @Nullable String notes) {
    LocalDate date = tradeDate != null ? tradeDate : LocalDate.now();
    TradeEntity entity = new TradeEntity(ticker, side, quantity, pricePerShare, date, notes);
    return repository.save(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public List<TradeEntity> listTrades(
      @Nullable String ticker,
      @Nullable String side,
      @Nullable LocalDate from,
      @Nullable LocalDate to) {
    if (ticker == null && side == null && from == null && to == null) {
      return repository.findAllByOrderByTradeDateDescCreatedAtDesc();
    }
    return repository.findFiltered(ticker, side, from, to);
  }

  @Override
  @Transactional(readOnly = true)
  public TradeEntity getTrade(long id) {
    return repository.findById(id).orElseThrow(() -> notFound(id));
  }

  @Override
  public void deleteTrade(long id) {
    if (!repository.existsById(id)) throw notFound(id);
    repository.deleteById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ClosedTrade> getClosedTrades() {
    List<TradeEntity> all = repository.findAllChronological();
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

    // Streak tracking
    int currentStreak = 0;
    String currentStreakType = "NONE";
    int bestWinStreak = 0, bestLossStreak = 0;
    int runWin = 0, runLoss = 0;

    // Sort by sell date for streak computation
    List<ClosedTrade> sorted =
        closed.stream()
            .sorted(Comparator.comparing(ClosedTrade::sellDate))
            .toList();

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

    currentStreak = Math.max(runWin, runLoss);
    currentStreakType = runWin > 0 ? "WIN" : (runLoss > 0 ? "LOSS" : "NONE");

    // Top tickers by absolute P&L, limit 5
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
        total,
        wins,
        losses,
        winRate,
        totalPnl,
        currentStreak,
        currentStreakType,
        bestWinStreak,
        bestLossStreak,
        avgHold,
        topTickers);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PnlHistoryEntry> getPnlHistory() {
    List<ClosedTrade> closed = getClosedTrades();
    List<ClosedTrade> sorted =
        closed.stream()
            .sorted(Comparator.comparing(ClosedTrade::sellDate))
            .toList();

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

  // ── FIFO matching ──

  static List<ClosedTrade> computeClosedTrades(List<TradeEntity> trades) {
    // Group by ticker
    Map<String, List<TradeEntity>> byTicker =
        trades.stream().collect(Collectors.groupingBy(TradeEntity::getTicker, LinkedHashMap::new, Collectors.toList()));

    List<ClosedTrade> result = new ArrayList<>();

    for (Map.Entry<String, List<TradeEntity>> entry : byTicker.entrySet()) {
      String ticker = entry.getKey();
      Deque<BuyLot> buyQueue = new ArrayDeque<>();

      for (TradeEntity t : entry.getValue()) {
        if ("BUY".equals(t.getSide())) {
          buyQueue.addLast(new BuyLot(t.getQuantity(), t.getPricePerShare(), t.getTradeDate()));
        } else {
          BigDecimal sellRemaining = t.getQuantity();
          while (sellRemaining.compareTo(BigDecimal.ZERO) > 0 && !buyQueue.isEmpty()) {
            BuyLot lot = buyQueue.peekFirst();
            BigDecimal matched = sellRemaining.min(lot.remaining);

            BigDecimal pnl =
                matched.multiply(t.getPricePerShare().subtract(lot.price));
            BigDecimal pnlPct =
                lot.price.compareTo(BigDecimal.ZERO) > 0
                    ? t.getPricePerShare()
                        .subtract(lot.price)
                        .divide(lot.price, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            long holdDays = ChronoUnit.DAYS.between(lot.date, t.getTradeDate());

            result.add(
                new ClosedTrade(
                    ticker,
                    matched,
                    lot.price,
                    t.getPricePerShare(),
                    lot.date,
                    t.getTradeDate(),
                    pnl,
                    pnlPct,
                    holdDays));

            lot.remaining = lot.remaining.subtract(matched);
            sellRemaining = sellRemaining.subtract(matched);
            if (lot.remaining.compareTo(BigDecimal.ZERO) == 0) {
              buyQueue.pollFirst();
            }
          }
        }
      }
    }

    return result;
  }

  private static EntityNotFoundException notFound(long id) {
    return new EntityNotFoundException("Trade not found: " + id);
  }

  private static class BuyLot {
    BigDecimal remaining;
    final BigDecimal price;
    final LocalDate date;

    BuyLot(BigDecimal qty, BigDecimal price, LocalDate date) {
      this.remaining = qty;
      this.price = price;
      this.date = date;
    }
  }
}
```

- [ ] **Step 4: Run FIFO tests to verify they pass**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew test --tests "*.DefaultTradeServiceTest" --no-daemon 2>&1 | tail -10`
Expected: All 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/austinharlan/trading_dashboard/service/DefaultTradeService.java \
        src/test/java/com/austinharlan/trading_dashboard/service/DefaultTradeServiceTest.java
git commit -m "feat: implement DefaultTradeService with FIFO matching

TDD: 5 unit tests covering single/partial/multi-lot sells,
unmatched sells, and multi-ticker independence."
```

---

### Task 7: Stats Computation Tests (TDD)

**Files:**
- Modify: `src/test/java/com/austinharlan/trading_dashboard/service/DefaultTradeServiceTest.java`

- [ ] **Step 1: Add stats tests to DefaultTradeServiceTest**

Append these tests to the existing `DefaultTradeServiceTest` class:

```java
  @Test
  void stats_winRateCalculation() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 10, 100.00, "2026-01-01"),
                trade("AAPL", "SELL", 10, 150.00, "2026-01-10"), // win
                trade("MSFT", "BUY", 10, 200.00, "2026-01-01"),
                trade("MSFT", "SELL", 10, 180.00, "2026-01-10"), // loss
                trade("NVDA", "BUY", 10, 50.00, "2026-01-01"),
                trade("NVDA", "SELL", 10, 80.00, "2026-01-10"))); // win

    TradeService.TradeStats stats = service.getStats();

    assertThat(stats.totalTrades()).isEqualTo(3);
    assertThat(stats.wins()).isEqualTo(2);
    assertThat(stats.losses()).isEqualTo(1);
    assertThat(stats.winRate()).isCloseTo(66.67, within(0.1));
  }

  @Test
  void stats_streakTracking() {
    // W, W, L, W — current streak is 1W, best win streak is 2
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("A", "BUY", 1, 10, "2026-01-01"),
                trade("A", "SELL", 1, 20, "2026-01-10"), // W
                trade("B", "BUY", 1, 10, "2026-01-11"),
                trade("B", "SELL", 1, 20, "2026-01-20"), // W
                trade("C", "BUY", 1, 20, "2026-01-21"),
                trade("C", "SELL", 1, 10, "2026-01-30"), // L
                trade("D", "BUY", 1, 10, "2026-02-01"),
                trade("D", "SELL", 1, 15, "2026-02-10"))); // W

    TradeService.TradeStats stats = service.getStats();

    assertThat(stats.currentStreak()).isEqualTo(1);
    assertThat(stats.currentStreakType()).isEqualTo("WIN");
    assertThat(stats.bestWinStreak()).isEqualTo(2);
    assertThat(stats.bestLossStreak()).isEqualTo(1);
  }

  @Test
  void stats_avgHoldDays() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 10, 100, "2026-01-01"),
                trade("AAPL", "SELL", 10, 150, "2026-01-11"), // 10 days
                trade("MSFT", "BUY", 10, 200, "2026-01-01"),
                trade("MSFT", "SELL", 10, 250, "2026-01-21"))); // 20 days

    TradeService.TradeStats stats = service.getStats();

    assertThat(stats.avgHoldDays()).isCloseTo(15.0, within(0.1));
  }

  @Test
  void stats_topTickersSortedByAbsolutePnl() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 10, 100, "2026-01-01"),
                trade("AAPL", "SELL", 10, 110, "2026-01-10"), // +100
                trade("MSFT", "BUY", 10, 200, "2026-01-01"),
                trade("MSFT", "SELL", 10, 150, "2026-01-10"), // -500
                trade("NVDA", "BUY", 10, 50, "2026-01-01"),
                trade("NVDA", "SELL", 10, 80, "2026-01-10"))); // +300

    TradeService.TradeStats stats = service.getStats();

    assertThat(stats.topTickers()).hasSize(3);
    // Sorted by |P&L| desc: MSFT(500), NVDA(300), AAPL(100)
    assertThat(stats.topTickers().get(0).ticker()).isEqualTo("MSFT");
    assertThat(stats.topTickers().get(1).ticker()).isEqualTo("NVDA");
    assertThat(stats.topTickers().get(2).ticker()).isEqualTo("AAPL");
  }

  @Test
  void stats_emptyTrades_returnsZeros() {
    when(repository.findAllChronological()).thenReturn(List.of());

    TradeService.TradeStats stats = service.getStats();

    assertThat(stats.totalTrades()).isEqualTo(0);
    assertThat(stats.winRate()).isEqualTo(0.0);
    assertThat(stats.currentStreakType()).isEqualTo("NONE");
  }
```

Also add this import at the top:

```java
import static org.assertj.core.api.Assertions.within;
```

- [ ] **Step 2: Run all tests to verify they pass**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew test --tests "*.DefaultTradeServiceTest" --no-daemon 2>&1 | tail -10`
Expected: All 10 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/austinharlan/trading_dashboard/service/DefaultTradeServiceTest.java
git commit -m "test: add stats computation tests (win rate, streaks, avg hold, top tickers)"
```

---

### Task 8: Controller — TradeController

**Files:**
- Create: `src/main/java/com/austinharlan/trading_dashboard/controllers/TradeController.java`

- [ ] **Step 1: Create the controller**

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
  public ResponseEntity<Trade> logTrade(@Valid LogTradeRequest request) {
    TradeEntity entity =
        tradeService.logTrade(
            request.getTicker().toUpperCase().strip(),
            request.getSide().getValue(),
            BigDecimal.valueOf(request.getQuantity()),
            BigDecimal.valueOf(request.getPricePerShare()),
            request.getTradeDate(),
            request.getNotes());
    return ResponseEntity.status(201).body(toDto(entity));
  }

  @Override
  public ResponseEntity<TradeListResponse> listTrades(
      String ticker, String side, LocalDate from, LocalDate to) {
    String normalizedTicker = ticker != null ? ticker.toUpperCase().strip() : null;
    List<TradeEntity> trades = tradeService.listTrades(normalizedTicker, side, from, to);
    TradeListResponse response =
        new TradeListResponse()
            .trades(trades.stream().map(this::toDto).toList());
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
            .closedTrades(
                closed.stream()
                    .map(
                        ct ->
                            new com.austinharlan.tradingdashboard.dto.ClosedTrade()
                                .ticker(ct.ticker())
                                .quantity(ct.quantity().doubleValue())
                                .buyPrice(ct.buyPrice().doubleValue())
                                .sellPrice(ct.sellPrice().doubleValue())
                                .buyDate(ct.buyDate())
                                .sellDate(ct.sellDate())
                                .pnl(ct.pnl().doubleValue())
                                .pnlPercent(ct.pnlPercent().doubleValue())
                                .holdDays((int) ct.holdDays()))
                    .toList());
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

  private Trade toDto(TradeEntity entity) {
    return new Trade()
        .id(entity.getId())
        .ticker(entity.getTicker())
        .side(Trade.SideEnum.fromValue(entity.getSide()))
        .quantity(entity.getQuantity().doubleValue())
        .pricePerShare(entity.getPricePerShare().doubleValue())
        .tradeDate(entity.getTradeDate())
        .notes(entity.getNotes())
        .createdAt(OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC));
  }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew compileJava --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

Note: The generated `TradesApi` interface method signatures may differ slightly from what's written above (parameter names, types). If compilation fails, read the generated `TradesApi.java` and adjust the controller method signatures to match exactly.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/austinharlan/trading_dashboard/controllers/TradeController.java
git commit -m "feat: add TradeController implementing generated TradesApi"
```

---

### Task 9: Integration Tests

**Files:**
- Create: `src/test/java/com/austinharlan/trading_dashboard/TradeIT.java`

- [ ] **Step 1: Create integration test class**

```java
package com.austinharlan.trading_dashboard;

import static org.assertj.core.api.Assertions.*;

import com.austinharlan.trading_dashboard.persistence.TradeEntity;
import com.austinharlan.trading_dashboard.persistence.TradeRepository;
import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TradeIT extends DatabaseIntegrationTest {

  @Autowired private TestRestTemplate rest;
  @Autowired private TradeRepository tradeRepository;

  private final HttpHeaders headers = new HttpHeaders();

  {
    headers.set("X-API-KEY", "test-api-key");
    headers.setContentType(MediaType.APPLICATION_JSON);
  }

  @AfterEach
  void cleanup() {
    tradeRepository.deleteAll();
  }

  @Test
  void postTrade_returns201AndPersists() {
    String body =
        """
        {"ticker":"AAPL","side":"BUY","quantity":10,"pricePerShare":150.0,"tradeDate":"2026-03-01"}
        """;

    ResponseEntity<Map> response =
        rest.exchange("/api/trades", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsEntry("ticker", "AAPL");
    assertThat(tradeRepository.count()).isEqualTo(1);
  }

  @Test
  void getTrades_returnsSortedByDateDesc() {
    tradeRepository.save(
        new TradeEntity("AAPL", "BUY", BigDecimal.TEN, BigDecimal.valueOf(100), LocalDate.of(2026, 1, 1), null));
    tradeRepository.save(
        new TradeEntity("MSFT", "BUY", BigDecimal.TEN, BigDecimal.valueOf(200), LocalDate.of(2026, 3, 1), null));

    ResponseEntity<Map> response =
        rest.exchange("/api/trades", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<Map> trades = (List<Map>) response.getBody().get("trades");
    assertThat(trades).hasSize(2);
    assertThat(trades.get(0).get("ticker")).isEqualTo("MSFT"); // newer first
  }

  @Test
  void getTrades_filtersByTicker() {
    tradeRepository.save(
        new TradeEntity("AAPL", "BUY", BigDecimal.TEN, BigDecimal.valueOf(100), LocalDate.of(2026, 1, 1), null));
    tradeRepository.save(
        new TradeEntity("MSFT", "BUY", BigDecimal.TEN, BigDecimal.valueOf(200), LocalDate.of(2026, 1, 1), null));

    ResponseEntity<Map> response =
        rest.exchange(
            "/api/trades?ticker=AAPL", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

    List<Map> trades = (List<Map>) response.getBody().get("trades");
    assertThat(trades).hasSize(1);
    assertThat(trades.get(0).get("ticker")).isEqualTo("AAPL");
  }

  @Test
  void deleteTrade_returns204() {
    TradeEntity saved =
        tradeRepository.save(
            new TradeEntity("AAPL", "BUY", BigDecimal.TEN, BigDecimal.valueOf(100), LocalDate.of(2026, 1, 1), null));

    ResponseEntity<Void> response =
        rest.exchange(
            "/api/trades/" + saved.getId(),
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(tradeRepository.count()).isEqualTo(0);
  }

  @Test
  void closedTrades_returnsFifoMatched() {
    tradeRepository.save(
        new TradeEntity("AAPL", "BUY", BigDecimal.TEN, BigDecimal.valueOf(100), LocalDate.of(2026, 1, 1), null));
    tradeRepository.save(
        new TradeEntity("AAPL", "SELL", BigDecimal.TEN, BigDecimal.valueOf(150), LocalDate.of(2026, 2, 1), null));

    ResponseEntity<Map> response =
        rest.exchange("/api/trades/closed", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<Map> closed = (List<Map>) response.getBody().get("closedTrades");
    assertThat(closed).hasSize(1);
    assertThat(((Number) closed.get(0).get("pnl")).doubleValue()).isCloseTo(500.0, within(0.01));
  }

  @Test
  void stats_returnsAggregatedData() {
    tradeRepository.save(
        new TradeEntity("AAPL", "BUY", BigDecimal.TEN, BigDecimal.valueOf(100), LocalDate.of(2026, 1, 1), null));
    tradeRepository.save(
        new TradeEntity("AAPL", "SELL", BigDecimal.TEN, BigDecimal.valueOf(150), LocalDate.of(2026, 2, 1), null));

    ResponseEntity<Map> response =
        rest.exchange("/api/trades/stats", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(((Number) response.getBody().get("wins")).intValue()).isEqualTo(1);
    assertThat(((Number) response.getBody().get("winRate")).doubleValue()).isCloseTo(100.0, within(0.1));
  }
}
```

Note: The integration test uses `test-api-key` header. Verify this matches the test profile's `TRADING_API_KEY` in `src/test/resources/application.properties`. If it uses a different value, adjust the header accordingly.

- [ ] **Step 2: Run integration tests**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew test --tests "*.TradeIT" --no-daemon 2>&1 | tail -15`
Expected: All tests PASS (or skipped if Docker unavailable locally). Fix any compilation or test failures before proceeding.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/austinharlan/trading_dashboard/TradeIT.java
git commit -m "test: add TradeIT integration tests for trade CRUD and FIFO matching"
```

---

### Task 10: Format and Full Build Verification

- [ ] **Step 1: Run spotless and full build**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew spotlessApply build --no-daemon 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 2: Commit any formatting changes**

```bash
git add -A
git diff --cached --quiet || git commit -m "style: apply spotless formatting"
```

---

### Task 11: Frontend — CSS and Tab Bar

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: Add tab bar CSS**

Add these CSS rules after the existing `.btn-remove:hover` rule (in the `<style>` section):

```css
    /* Portfolio tab bar */
    .port-tab-bar { display: flex; gap: 0; border-bottom: 1px solid var(--border); margin-bottom: 20px; }
    .port-tab {
      padding: 10px 20px; font-size: 11px; letter-spacing: .8px; text-transform: uppercase;
      color: var(--text-dim); cursor: pointer; border-bottom: 2px solid transparent;
      transition: color .2s, border-color .2s;
      font-family: var(--mono); background: none; border-top: none; border-left: none; border-right: none;
    }
    .port-tab:hover { color: var(--text-mid); }
    .port-tab.active { color: var(--green); border-bottom-color: var(--green); }
    /* Trade calendar */
    .trade-calendar { display: grid; grid-template-columns: repeat(7, 1fr); gap: 3px; }
    .trade-cal-head { font-size: 7px; color: var(--text-muted); text-align: center; }
    .trade-cal-cell { aspect-ratio: 1; border-radius: 3px; cursor: default; }
    /* Trade charts row */
    .trade-charts-row { display: grid; grid-template-columns: 1.4fr 1fr; gap: 12px; margin-bottom: 18px; }
    /* Trade top tickers row */
    .trade-tickers-row { display: flex; gap: 16px; }
    .trade-ticker-item { flex: 1; display: flex; justify-content: space-between; padding: 4px 0; border-bottom: 1px solid rgba(255,255,255,.04); font-size: 11px; }
```

- [ ] **Step 2: Replace Portfolio page HTML with tab structure**

Replace the `<!-- Portfolio -->` section (the `<div class="page" id="page-portfolio">` and its contents) with:

```html
      <!-- Portfolio -->
      <div class="page" id="page-portfolio">
        <div class="port-tab-bar">
          <button class="port-tab active" data-port-tab="positions" onclick="switchPortTab('positions')">Positions</button>
          <button class="port-tab" data-port-tab="trades" onclick="switchPortTab('trades')">Trades</button>
        </div>
        <div id="port-tab-positions">
          <div id="pnl-banner-wrap"></div>
          <div class="stat-row" id="port-stats"></div>
          <div class="daily-quote" id="dailyQuote">
            <button class="quote-refresh-btn" id="quoteRefreshBtn" title="New quote" aria-label="New quote">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
              </svg>
            </button>
            <div class="daily-quote-mark">&ldquo;</div>
            <div>
              <div class="daily-quote-text" id="dailyQuoteText"></div>
              <div class="daily-quote-attr" id="dailyQuoteAttr">Warren Buffett</div>
            </div>
          </div>
          <div class="chart-wrap" id="port-treemap-wrap" style="display:none">
            <div class="chart-title">Allocation by Market Value</div>
            <canvas id="portTreemap"></canvas>
          </div>
          <div class="chart-wrap" id="port-sparks-wrap" style="display:none">
            <div class="spark-header">
              <div class="chart-title" style="margin-bottom:0">3-Month Performance</div>
              <div class="spark-sort-toggle">
                <button class="tf-btn active" onclick="sortSparks('alpha')">A–Z</button>
                <button class="tf-btn" onclick="sortSparks('top')">Top</button>
                <button class="tf-btn" onclick="sortSparks('bottom')">Bottom</button>
              </div>
            </div>
            <div class="port-sparks-grid" id="port-sparks-grid"></div>
          </div>
          <div class="section-head" style="margin-bottom:14px;margin-top:22px">
            <span class="section-title">Positions</span>
            <button class="btn btn-primary btn-sm" onclick="openAddPosition()">+ Add Position</button>
          </div>
          <div class="table-wrap" id="port-table">
            <div class="state-box"><span class="spinner"></span></div>
          </div>
        </div>
        <div id="port-tab-trades" style="display:none">
          <div class="state-box"><span class="spinner"></span></div>
        </div>
      </div>
```

- [ ] **Step 3: Add switchPortTab function**

Add this function in the JavaScript section, near the `loadPortfolio` function:

```javascript
  function switchPortTab(tab) {
    document.querySelectorAll('.port-tab').forEach(t => t.classList.remove('active'));
    document.querySelector(`.port-tab[data-port-tab="${tab}"]`).classList.add('active');
    document.getElementById('port-tab-positions').style.display = tab === 'positions' ? '' : 'none';
    document.getElementById('port-tab-trades').style.display = tab === 'trades' ? '' : 'none';
    if (tab === 'trades') loadTrades();
  }
```

- [ ] **Step 4: Verify build**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew spotlessApply build --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "feat: add Positions | Trades tab bar to Portfolio page"
```

---

### Task 12: Frontend — Trades Tab Content

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: Add the loadTrades function and rendering**

Add these functions in the JavaScript section, near `switchPortTab`:

```javascript
  async function loadTrades() {
    const container = document.getElementById('port-tab-trades');
    container.innerHTML = '<div class="state-box"><span class="spinner"></span></div>';

    try {
      const [statsRes, closedRes, historyRes, calRes] = await Promise.all([
        get('/api/trades/stats'),
        get('/api/trades/closed'),
        get('/api/trades/pnl-history'),
        get('/api/trades/calendar'),
      ]);

      const stats = statsRes;
      const closed = closedRes?.closedTrades || [];
      const history = historyRes?.entries || [];
      const calendar = calRes?.entries || [];

      if (stats.totalTrades === 0) {
        container.innerHTML = `
          <div class="state-box" style="padding:60px 0">
            <div style="font-size:11px;color:var(--text-dim);margin-bottom:8px;">No trades logged yet</div>
            <div style="font-size:10px;color:var(--text-muted);margin-bottom:16px;">Start tracking your performance by logging your first trade.</div>
            <button class="btn btn-primary btn-sm" onclick="openLogTrade()">+ Log Trade</button>
          </div>`;
        return;
      }

      // Stats row
      const winCls = stats.winRate >= 50 ? 'green' : 'red';
      const pnlCls = stats.totalPnl >= 0 ? 'green' : 'red';
      const pnlSign = stats.totalPnl >= 0 ? '+' : '';
      const streakLabel = stats.currentStreakType === 'WIN' ? 'W' : (stats.currentStreakType === 'LOSS' ? 'L' : '');

      let html = `
        <div style="display:flex;justify-content:flex-end;margin-bottom:16px;">
          <button class="btn btn-primary btn-sm" onclick="openLogTrade()">+ Log Trade</button>
        </div>
        <div class="stat-row">
          ${statCard('Win Rate', stats.winRate.toFixed(1) + '%', winCls, stats.wins + 'W · ' + stats.losses + 'L', null)}
          ${statCard('Total P&L', pnlSign + money(stats.totalPnl), pnlCls, '', null)}
          ${statCard('Streak', stats.currentStreak + streakLabel, '', 'Best: ' + stats.bestWinStreak + 'W', null)}
          ${statCard('Avg Hold', Math.round(stats.avgHoldDays) + 'd', '', '', null)}
        </div>`;

      // Charts row
      html += `<div class="trade-charts-row">`;

      // Cumulative P&L chart
      html += `<div class="chart-wrap">
        <div class="chart-title">Cumulative P&L</div>
        <canvas id="tradePnlChart" style="width:100%;height:80px;"></canvas>
      </div>`;

      // Calendar heatmap
      html += `<div class="chart-wrap">
        <div class="chart-title">${renderTradeCalendarTitle()}</div>
        <div class="trade-calendar">
          ${renderTradeCalendar(calendar)}
        </div>
      </div>`;

      html += `</div>`;

      // Top tickers
      if (stats.topTickers && stats.topTickers.length > 0) {
        html += `<div class="chart-wrap" style="margin-bottom:18px;">
          <div class="chart-title">Top Tickers by P&L</div>
          <div class="trade-tickers-row">
            ${stats.topTickers.map(t => {
              const cls = t.pnl >= 0 ? 'color:var(--green)' : 'color:var(--red)';
              const sign = t.pnl >= 0 ? '+' : '';
              return `<div class="trade-ticker-item">
                <span style="${cls}">${t.ticker}</span>
                <span style="${cls}">${sign}${money(t.pnl)}</span>
              </div>`;
            }).join('')}
          </div>
        </div>`;
      }

      // Closed trades table
      html += `<div class="section-head" style="margin-bottom:10px">
        <span class="section-title">Closed Trades</span>
      </div>`;

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
              return `<tr>
                <td class="${cls}"><strong>${ct.ticker}</strong></td>
                <td>${num(ct.quantity)}</td>
                <td>${money(ct.buyPrice)}</td>
                <td>${money(ct.sellPrice)}</td>
                <td class="${cls}">${sign}${money(ct.pnl)}</td>
                <td>${ct.holdDays}d</td>
              </tr>`;
            }).join('')}
          </tbody>
        </table></div>`;
      }

      container.innerHTML = html;

      // Draw cumulative P&L chart
      if (history.length > 0) drawTradePnlChart(history);

    } catch (err) {
      console.error('Failed to load trades', err);
      container.innerHTML = `<div class="state-box" style="color:var(--red)">Failed to load trades.</div>`;
    }
  }

  function renderTradeCalendarTitle() {
    const now = new Date();
    return now.toLocaleString('en-US', { month: 'long' }) + ' ' + now.getFullYear();
  }

  function renderTradeCalendar(calEntries) {
    const now = new Date();
    const year = now.getFullYear(), month = now.getMonth();
    const firstDay = new Date(year, month, 1).getDay(); // 0=Sun
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const offset = firstDay === 0 ? 6 : firstDay - 1; // Mon-start

    const pnlMap = {};
    calEntries.forEach(e => { pnlMap[e.date] = e.pnl; });

    const maxAbs = Math.max(...calEntries.map(e => Math.abs(e.pnl)), 1);

    let cells = 'MTWTFSS'.split('').map(d => `<div class="trade-cal-head">${d}</div>`).join('');
    for (let i = 0; i < offset; i++) cells += '<div class="trade-cal-cell"></div>';
    for (let d = 1; d <= daysInMonth; d++) {
      const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const pnl = pnlMap[dateStr];
      let bg = '';
      if (pnl != null) {
        const intensity = Math.min(Math.abs(pnl) / maxAbs, 1) * 0.6 + 0.15;
        bg = pnl >= 0
          ? `background:rgba(78,221,138,${intensity.toFixed(2)})`
          : `background:rgba(240,120,104,${intensity.toFixed(2)})`;
      }
      const title = pnl != null ? `title="${dateStr}: ${pnl >= 0 ? '+' : ''}${money(pnl)}"` : '';
      cells += `<div class="trade-cal-cell" style="${bg}" ${title}></div>`;
    }
    return cells;
  }

  function drawTradePnlChart(history) {
    const canvas = document.getElementById('tradePnlChart');
    if (!canvas) return;
    const dpr = window.devicePixelRatio || 1;
    const W = canvas.parentElement.clientWidth - 24;
    const H = 80;
    canvas.width = W * dpr;
    canvas.height = H * dpr;
    canvas.style.width = W + 'px';
    canvas.style.height = H + 'px';

    const ctx = canvas.getContext('2d');
    ctx.scale(dpr, dpr);

    const values = history.map(e => e.cumulativePnl);
    const max = Math.max(...values, 0);
    const min = Math.min(...values, 0);
    const range = max - min || 1;
    const pad = 4;

    ctx.beginPath();
    values.forEach((v, i) => {
      const x = pad + (i / (values.length - 1 || 1)) * (W - 2 * pad);
      const y = pad + ((max - v) / range) * (H - 2 * pad);
      if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
    });
    ctx.strokeStyle = values[values.length - 1] >= 0 ? 'rgba(78,221,138,.8)' : 'rgba(240,120,104,.8)';
    ctx.lineWidth = 1.5;
    ctx.stroke();

    // Fill area
    const lastX = pad + ((values.length - 1) / (values.length - 1 || 1)) * (W - 2 * pad);
    const zeroY = pad + ((max - 0) / range) * (H - 2 * pad);
    ctx.lineTo(lastX, zeroY);
    ctx.lineTo(pad, zeroY);
    ctx.closePath();
    ctx.fillStyle = values[values.length - 1] >= 0 ? 'rgba(78,221,138,.08)' : 'rgba(240,120,104,.08)';
    ctx.fill();
  }
```

- [ ] **Step 2: Verify build**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew spotlessApply build --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "feat: render trades tab with stats, charts, calendar, and closed trades table"
```

---

### Task 13: Frontend — Log Trade Modal and Position Link Prompt

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: Add Log Trade modal HTML**

Add this modal HTML after the existing `addPositionModal` `div`:

```html
<div class="modal-backdrop" id="logTradeModal">
  <div class="modal">
    <h3>Log Trade</h3>
    <div class="field-group">
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
      </div>
      <div>
        <div class="field-label">Quantity</div>
        <input class="field" id="lt-qty" type="number" placeholder="10" min="0.0001" step="any">
      </div>
      <div>
        <div class="field-label">Price / Share ($)</div>
        <input class="field" id="lt-price" type="number" placeholder="163.42" min="0.0001" step="any">
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

- [ ] **Step 2: Add CSS for side toggle buttons**

Add after the `.port-tab.active` CSS rule:

```css
    .lt-side-btn { border: 1px solid var(--border); color: var(--text-dim); background: var(--bg-card); }
    .lt-side-btn.active { background: rgba(78,221,138,.15); border-color: rgba(78,221,138,.3); color: var(--green); }
```

- [ ] **Step 3: Add Log Trade JavaScript functions**

Add these functions near the `switchPortTab` function:

```javascript
  let _tradeSide = 'BUY';

  function setTradeSide(side) {
    _tradeSide = side;
    document.querySelectorAll('.lt-side-btn').forEach(b => {
      b.classList.toggle('active', b.dataset.side === side);
    });
  }

  function openLogTrade() {
    _tradeSide = 'BUY';
    document.getElementById('lt-ticker').value = '';
    document.getElementById('lt-qty').value = '';
    document.getElementById('lt-price').value = '';
    document.getElementById('lt-date').value = new Date().toISOString().slice(0, 10);
    document.getElementById('lt-notes').value = '';
    document.getElementById('lt-err').textContent = '';
    document.getElementById('lt-link-prompt').style.display = 'none';
    document.getElementById('lt-actions').style.display = '';
    setTradeSide('BUY');
    document.getElementById('logTradeModal').classList.add('open');
    document.getElementById('lt-ticker').focus();
  }

  function closeLogTrade() {
    document.getElementById('logTradeModal').classList.remove('open');
  }

  async function submitLogTrade() {
    const ticker = document.getElementById('lt-ticker').value.trim().toUpperCase();
    const qty = parseFloat(document.getElementById('lt-qty').value);
    const price = parseFloat(document.getElementById('lt-price').value);
    const date = document.getElementById('lt-date').value;
    const notes = document.getElementById('lt-notes').value.trim() || null;
    const errEl = document.getElementById('lt-err');
    const btn = document.getElementById('lt-submit');

    if (!ticker) { errEl.textContent = 'Ticker is required.'; return; }
    if (!(qty > 0)) { errEl.textContent = 'Quantity must be greater than 0.'; return; }
    if (!(price > 0)) { errEl.textContent = 'Price must be greater than 0.'; return; }
    if (!date) { errEl.textContent = 'Date is required.'; return; }

    btn.disabled = true;
    errEl.textContent = '';
    try {
      const r = await fetch('/api/trades', {
        method: 'POST',
        headers: { 'X-API-KEY': KEY, 'Content-Type': 'application/json' },
        body: JSON.stringify({ ticker, side: _tradeSide, quantity: qty, pricePerShare: price, tradeDate: date, notes }),
      });
      if (!r.ok) {
        const body = await r.json().catch(() => ({}));
        errEl.textContent = body.message || 'Failed to log trade.';
        return;
      }

      // Check if ticker matches an existing position
      const posRes = await get('/api/portfolio/positions').catch(() => null);
      const positions = posRes?.positions || [];
      const existing = positions.find(p => p.ticker === ticker);

      if (existing) {
        showPositionLinkPrompt(ticker, _tradeSide, qty, price, existing);
      } else {
        closeLogTrade();
        loadTrades();
      }
    } catch (err) {
      console.error('Log trade error', err);
      errEl.textContent = 'Network error — check your connection.';
    } finally {
      btn.disabled = false;
    }
  }

  function showPositionLinkPrompt(ticker, side, qty, price, existing) {
    const prompt = document.getElementById('lt-link-prompt');
    document.getElementById('lt-actions').style.display = 'none';

    let delta, newTotal;
    if (side === 'BUY') {
      newTotal = existing.quantity + qty;
      delta = `+${num(qty)} shares at ${money(price)} → new total: ${num(newTotal)} shares`;
    } else {
      newTotal = existing.quantity - qty;
      delta = `−${num(qty)} shares → new total: ${num(Math.max(newTotal, 0))} shares`;
    }

    prompt.innerHTML = `
      <div style="margin-top:16px;background:rgba(78,221,138,.06);border:1px dashed rgba(78,221,138,.2);border-radius:6px;padding:12px;text-align:center;">
        <div style="font-size:10px;color:var(--green);margin-bottom:8px;">You hold ${ticker} — update position?</div>
        <div style="font-size:10px;color:var(--text-muted);">${delta}</div>
        <div style="display:flex;gap:8px;justify-content:center;margin-top:10px;">
          <button class="btn btn-sm" onclick="dismissPositionLink()">Dismiss</button>
          <button class="btn btn-primary btn-sm" onclick="acceptPositionLink('${ticker}','${side}',${qty},${price},${existing.quantity},${existing.cost_basis})">Update Position</button>
        </div>
      </div>`;
    prompt.style.display = '';
  }

  function dismissPositionLink() {
    closeLogTrade();
    loadTrades();
  }

  async function acceptPositionLink(ticker, side, qty, price, existingQty, existingBasis) {
    try {
      if (side === 'BUY') {
        const newQty = existingQty + qty;
        const newBasis = existingBasis + (qty * price);
        const avgPrice = newBasis / newQty;
        await fetch('/api/portfolio/positions', {
          method: 'POST',
          headers: { 'X-API-KEY': KEY, 'Content-Type': 'application/json' },
          body: JSON.stringify({ ticker, quantity: newQty, price_per_share: avgPrice }),
        });
      } else {
        const newQty = existingQty - qty;
        if (newQty <= 0) {
          await fetch('/api/portfolio/positions/' + ticker, {
            method: 'DELETE',
            headers: { 'X-API-KEY': KEY },
          });
        } else {
          const avgPrice = existingBasis / existingQty;
          await fetch('/api/portfolio/positions', {
            method: 'POST',
            headers: { 'X-API-KEY': KEY, 'Content-Type': 'application/json' },
            body: JSON.stringify({ ticker, quantity: newQty, price_per_share: avgPrice }),
          });
        }
      }
    } catch (err) {
      console.error('Position link error', err);
    }
    closeLogTrade();
    loadTrades();
    loadPortfolio(); // Refresh positions tab too
  }

  // Modal backdrop click to close
  document.getElementById('logTradeModal').addEventListener('click', e => {
    if (e.target === e.currentTarget) closeLogTrade();
  });
```

- [ ] **Step 4: Verify build**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew spotlessApply build --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "feat: add log trade modal with position link prompt"
```

---

### Task 14: Final Build, Format, and Push

- [ ] **Step 1: Run full build**

Run: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew spotlessApply build --no-daemon 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Commit any remaining formatting changes**

```bash
cd /Users/aharlan/Documents/Code/Benji
git add -A
git diff --cached --quiet || git commit -m "style: final formatting pass"
```

- [ ] **Step 3: Push to remote**

```bash
git push
```
