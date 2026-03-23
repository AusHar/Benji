# Trade Tracking on Portfolio Page

**Date:** 2026-03-23
**Status:** Approved

## Overview

Add manual trade logging and performance analytics to the Portfolio page. Trades are independent entities with optional position linking. FIFO matching pairs buys and sells to compute closed trades, P&L, and performance stats.

## UI Layout

### Tab Bar

The Portfolio page gains a **Positions | Trades** tab bar at the top. The Positions tab contains all existing Portfolio content unchanged. The Trades tab contains the new trade performance view.

The tab bar must use existing styling patterns:
- Inactive tab: `color: var(--text-dim)`, transparent bottom border
- Active tab: `color: var(--green)`, `border-bottom: 2px solid` with green, subtle glow

### Trades Tab — Top to Bottom

1. **"+ Log Trade" button** — Top right, uses `.btn.btn-primary.btn-sm` like the existing "+ Add Position" button.

2. **Stats row** — Uses existing `.stat-row` container with stat boxes. Four stats:
   - **Win Rate** — percentage, W/L count below. Green if >= 50%, red if < 50%.
   - **Total P&L** — dollar amount. Green/red coloring via `.stat-value.green` / `.stat-value.red`.
   - **Streak** — current streak (e.g., "3W" or "2L"), best streak below. Uses existing `.stat-label` / `.stat-value`.
   - **Avg Hold** — days, e.g., "12d". Neutral color (`var(--text)`).

3. **Charts row** — Two `.chart-wrap` containers in a CSS grid (`grid-template-columns: 1.4fr 1fr; gap: 12px`):
   - **Cumulative P&L chart** — Canvas-based line chart, same approach as existing sparklines. Title via `.chart-title`. Green line for positive territory, red when cumulative is negative.
   - **Trade calendar heatmap** — Grid of day cells for the current month. Green intensity = positive P&L magnitude, red intensity = negative. Same approach as the journal's calendar (`.j-cal-grid`, `.j-cal-head`, `.j-cal-day`) but with red/green instead of single-color. Day headers (M T W T F S S) in `var(--text-muted)`.

4. **Top Tickers by P&L** — A `.chart-wrap` container with `.chart-title`. Horizontal row showing top 5 tickers by absolute P&L. Green for positive, `var(--red)` for negative. Sorted by absolute P&L descending.

5. **Closed Trades table** — Uses existing `.table-wrap` with the same table structure as positions/transactions:
   - Header row: TICKER, QTY, BUY, SELL, P&L, HELD
   - Uses `<table class="data-table">` with `<th>` / `<td>` elements, matching existing positions and transactions tables
   - Ticker colored green for wins, red for losses
   - P&L column colored green/red
   - Sorted by sell date descending (most recent first)

### Log Trade Modal

Reuses existing modal infrastructure (`.modal-backdrop`, `.modal`, `.field-group`, `.field-label`, `.field`, `.modal-err`, `.modal-actions`).

**Fields:**
- **Ticker** — `.field` text input, uppercase enforced
- **Side** — Toggle between BUY/SELL. Two buttons side by side; active one uses green background (`rgba(78,221,138,.15)` + green border), inactive uses default card styling
- **Quantity** — `.field` number input
- **Price / Share** — `.field` number input
- **Date** — `.field` date input, defaults to today
- **Notes** — `.field` textarea, optional

**Buttons:** Cancel (`.btn.btn-sm`) and Log Trade (`.btn.btn-primary.btn-sm`), in `.modal-actions`.

### Position Link Prompt

After successfully saving a trade, if the ticker matches an existing portfolio holding:
- A prompt appears below the modal (or replaces the modal content)
- Dashed green border (`border: 1px dashed rgba(78,221,138,.2)`), subtle green background
- Text: "You hold {TICKER} — update position?"
- Shows the delta: "+10 shares at $200.50 → new total: 60 shares" (for buys) or "-10 shares → new total: 40 shares" (for sells)
- Two buttons: Dismiss and Update Position
- **Update Position** — The existing `POST /api/portfolio/positions` is an upsert that *replaces* the position entirely. So the frontend must:
  1. Fetch the current position for the ticker (from the already-loaded positions list)
  2. Compute the new total quantity and weighted average cost basis
  3. Call `POST /api/portfolio/positions` with the updated totals
  - For sells that fully close the position, call `DELETE /api/portfolio/positions/{ticker}` instead
  - For sells that partially reduce a position, compute reduced quantity and POST the replacement
- **Dismiss** closes the prompt, no position change.

### Empty State

When no trades exist yet, the Trades tab shows a centered empty state:
- Text: "No trades logged yet"
- Subtitle: "Start tracking your performance by logging your first trade."
- A centered "+ Log Trade" button
- Same empty-state pattern as existing `.state-box` styling

## Data Model

### `trades` table (Flyway migration `V4__trades.sql`)

```sql
CREATE TABLE trades (
    id          BIGSERIAL PRIMARY KEY,
    ticker      VARCHAR(12) NOT NULL,
    side        VARCHAR(4)  NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity    DECIMAL(12,4) NOT NULL CHECK (quantity > 0),
    price_per_share DECIMAL(12,4) NOT NULL CHECK (price_per_share > 0),
    trade_date  DATE        NOT NULL DEFAULT CURRENT_DATE,
    notes       TEXT,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trades_ticker ON trades (ticker);
CREATE INDEX idx_trades_trade_date ON trades (trade_date);
```

## API Design

All endpoints under `/api/trades`. Added to `openAPI.yaml` following existing conventions.

### Endpoints

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/api/trades` | Log a new trade | `201` with `Trade` body |
| `GET` | `/api/trades` | List all trades | `200` with `TradeListResponse` |
| `GET` | `/api/trades/{id}` | Get single trade | `200` with `Trade` |
| `DELETE` | `/api/trades/{id}` | Delete a trade | `204` |
| `GET` | `/api/trades/stats` | Aggregated stats | `200` with `TradeStats` |
| `GET` | `/api/trades/closed` | FIFO-matched closed trades | `200` with `ClosedTradeListResponse` |
| `GET` | `/api/trades/pnl-history` | Cumulative P&L series | `200` with `PnlHistoryResponse` |
| `GET` | `/api/trades/calendar` | Daily P&L by date | `200` with `TradeCalendarResponse` |

### Query Parameters for `GET /api/trades`

- `ticker` (optional) — filter by ticker
- `side` (optional) — `BUY` or `SELL`
- `from` (optional) — start date (inclusive)
- `to` (optional) — end date (inclusive)

Default sort: `trade_date DESC, created_at DESC` (newest first).

### Request/Response Schemas

**`LogTradeRequest`:**
```json
{
  "ticker": "AAPL",
  "side": "BUY",
  "quantity": 10,
  "pricePerShare": 200.50,
  "tradeDate": "2026-03-23",
  "notes": "Bought the dip"
}
```
- `ticker`: required, string
- `side`: required, enum `BUY` | `SELL`
- `quantity`: required, number > 0
- `pricePerShare`: required, number > 0
- `tradeDate`: optional, date string (defaults to today)
- `notes`: optional, string

**`Trade`:**
```json
{
  "id": 1,
  "ticker": "AAPL",
  "side": "BUY",
  "quantity": 10,
  "pricePerShare": 200.50,
  "tradeDate": "2026-03-23",
  "notes": "Bought the dip",
  "createdAt": "2026-03-23T05:07:00Z"
}
```

**`ClosedTrade`:**
```json
{
  "ticker": "AAPL",
  "quantity": 10,
  "buyPrice": 200.50,
  "sellPrice": 225.00,
  "buyDate": "2026-03-01",
  "sellDate": "2026-03-23",
  "pnl": 245.00,
  "pnlPercent": 12.22,
  "holdDays": 22
}
```

**`TradeStats`:**
```json
{
  "totalTrades": 25,
  "wins": 17,
  "losses": 8,
  "winRate": 68.0,
  "totalPnl": 4280.00,
  "currentStreak": 3,
  "currentStreakType": "WIN",
  "bestWinStreak": 7,
  "bestLossStreak": 3,
  "avgHoldDays": 12.4,
  "topTickers": [
    { "ticker": "NVDA", "pnl": 2140.00, "tradeCount": 3 },
    { "ticker": "AAPL", "pnl": 1820.00, "tradeCount": 5 }
  ]
}
```

**`PnlHistoryEntry`:**
```json
{
  "date": "2026-03-23",
  "pnl": 245.00,
  "cumulativePnl": 4280.00
}
```

**`TradeCalendarEntry`:**
```json
{
  "date": "2026-03-23",
  "pnl": 245.00,
  "tradeCount": 2
}
```

## Backend Architecture

### Package Structure

All new classes under `com.austinharlan.trading_dashboard`:

- `controllers/TradeController` — implements generated `TradeApi` interface
- `service/TradeService` — interface
- `service/DefaultTradeService` — implementation with FIFO matching
- `persistence/TradeEntity` — JPA entity
- `persistence/TradeRepository` — Spring Data JPA repository

### FIFO Matching Algorithm

Located in `DefaultTradeService`. Computes closed trades at read time (not stored).

```
For each ticker with both BUY and SELL trades:
  1. Collect all trades, ordered by trade_date ASC, created_at ASC
  2. Maintain a queue of open buy lots: (remaining_qty, price, date)
  3. For each SELL trade in order:
     a. While sell_remaining > 0 and buy_queue is not empty:
        - Take front of buy queue
        - matched_qty = min(buy_lot.remaining, sell_remaining)
        - Emit ClosedTrade(ticker, matched_qty, buy_price, sell_price, buy_date, sell_date)
        - Reduce both remaining quantities
        - If buy lot exhausted, remove from queue
     b. If sell_remaining > 0 with no buys left, the excess is unmatched (ignored)
```

### Stats Computation

All derived from the list of closed trades returned by FIFO matching:

- **Win/Loss:** count where `pnl > 0` vs `pnl <= 0`; `winRate = wins / total * 100`
- **Streaks:** walk closed trades by sell date; track consecutive wins/losses; record current and best
- **Avg hold time:** mean of `holdDays` across all closed trades
- **Top tickers:** group by ticker, sum P&L, count trades; sort by absolute P&L descending; limit to top 5
- **Calendar:** group closed trades by sell date, sum P&L per day
- **P&L history:** walk closed trades by sell date, emit running cumulative sum

## Testing

### Unit Tests

**`DefaultTradeServiceTest`:**
- FIFO: single buy + single sell → one closed trade with correct P&L
- FIFO: partial sell (buy 100, sell 50) → closed trade for 50, 50 remain open
- FIFO: multiple buys consumed by one large sell
- FIFO: sell with no matching buys → no closed trade
- FIFO: interleaved buys/sells across multiple tickers → correct per-ticker matching
- Stats: win rate calculation
- Stats: streak tracking (win streaks, loss streaks, mixed)
- Stats: average hold days
- Stats: top tickers sorted by absolute P&L

**`TradeControllerTest`:**
- Delegates to service, returns correct HTTP status codes
- Validates request fields (missing ticker, negative quantity, etc.)

### Integration Tests

**`TradeIT` (extends `DatabaseIntegrationTest`):**
- POST trade → 201, verify persisted
- GET trades → returns all, sorted by date desc
- GET trades with ticker filter → returns only matching
- GET trades with date range → returns only in range
- DELETE trade → 204, verify removed
- GET /trades/closed → FIFO matching produces correct closed trades
- GET /trades/stats → correct aggregated stats
- GET /trades/pnl-history → correct cumulative series
- GET /trades/calendar → correct daily P&L
- Flyway migration applies cleanly (implicit — app context starts)

## UI Styling Reference

All new UI must use existing CSS variables and classes. No new CSS classes unless absolutely necessary for trade-specific layout.

### Variables to Use
- Colors: `var(--green)`, `var(--red)`, `var(--amber)`, `var(--text)`, `var(--text-mid)`, `var(--text-dim)`, `var(--text-muted)`
- Backgrounds: `var(--bg-card)`, `var(--bg-card-hi)`
- Borders: `var(--border)`, `var(--border-mid)`
- Glows: `var(--glow-sm)`, `var(--glow-ring)`
- Fonts: `var(--mono)` for all UI text

### Classes to Reuse
- Layout: `.stat-row`, `.stat-label`, `.stat-value`, `.section-head`, `.section-title`
- Tables: `.table-wrap`, `.data-table` with `<th>` / `<td>` elements
- Charts: `.chart-wrap`, `.chart-title`
- Forms: `.field-row`, `.field`, `.field-label`
- Buttons: `.btn`, `.btn-primary`, `.btn-sm`, `.btn-remove`
- Modal: `.modal-backdrop`, `.modal`, `.field-group`, `.modal-err`, `.modal-actions`

### New CSS (minimal)
- `.port-tab-bar` — flex container for tabs, bottom border
- `.port-tab` — individual tab styling (active/inactive states matching nav-item pattern)
- `.trade-calendar` — grid layout for the heatmap cells (similar to `.j-cal-grid`)
- `.trade-cal-cell` — individual day cell with dynamic background color
