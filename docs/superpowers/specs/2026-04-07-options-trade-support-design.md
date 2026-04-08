# Options Trade Support — Design Spec

**Date:** 2026-04-07
**Status:** Approved
**Approach:** Extend existing trades table with nullable option columns (Approach A)

## Overview

Add options trade tracking alongside existing equity trades. Options are identified by a contract key (ticker + optionType + strikePrice + expirationDate) and support four side types: BUY, SELL, EXPIRE, and EXERCISE. The FIFO lot matcher is extended to handle bidirectional matching (long and short positions), and EXERCISE auto-creates linked equity trades.

Key use case: covered calls — sell calls against owned equity, then buy back, let expire, or get assigned.

## Data Model

### New Columns on `trades` Table (Flyway V6)

| Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|
| `asset_type` | `VARCHAR(6)` | NOT NULL | `'EQUITY'` | `EQUITY` or `OPTION` |
| `option_type` | `VARCHAR(4)` | YES | null | `CALL` or `PUT`. Null for equities. |
| `strike_price` | `DECIMAL(12,4)` | YES | null | Strike price. Null for equities. |
| `expiration_date` | `DATE` | YES | null | Contract expiration. Null for equities. |
| `multiplier` | `INTEGER` | NOT NULL | `1` | `100` for options, `1` for equities. |
| `linked_trade_id` | `BIGINT` | YES | null | Self-referencing FK. Links EXERCISE option trades to their auto-created equity trades (bidirectional). |

### Constraint Changes

- `side` CHECK expands from `('BUY', 'SELL')` to `('BUY', 'SELL', 'EXPIRE', 'EXERCISE')`
- New CHECK: `asset_type IN ('EQUITY', 'OPTION')`
- New CHECK: `option_type IS NULL OR option_type IN ('CALL', 'PUT')`
- New FK: `linked_trade_id REFERENCES trades(id)`
- New index: `(user_id, ticker, asset_type, option_type, strike_price, expiration_date)` for contract identity lookups

### Backward Compatibility

All existing equity rows get `asset_type='EQUITY'`, `multiplier=1`, option fields null via column defaults. Zero data migration required.

### `price_per_share` Reuse

For options, `price_per_share` stores the premium per unit (per-share equivalent). Example: a $4.50 premium is stored as `4.50`. P&L multiplies by `quantity × multiplier`.

## Lot Matching & P&L

### Contract Key

Grouping key changes from `ticker` to:

- **Equities:** `(ticker, EQUITY, null, null)`
- **Options:** `(ticker, optionType, strikePrice, expirationDate)`

### Bidirectional FIFO Matching

The current matcher only handles buy-first (long) positions. Extended to support sell-first (short) positions:

```
For each trade in chronological order within a ContractKey:
  If opposite-side queue has lots → match against them (closing)
  Otherwise → add to same-side queue (opening)
```

Long position: BUY adds to buyQueue, SELL matches against buyQueue.
Short position: SELL adds to sellQueue, BUY matches against sellQueue.

### P&L Formula

```
pnl = (sellPrice - buyPrice) × matchedQty × multiplier
```

Where `buyPrice` and `sellPrice` are literally the prices on the BUY and SELL legs, regardless of which was the open or close. This formula is correct for both long and short positions:

- Long: buy $5, sell $8 → ($8 - $5) × qty × mult = +profit
- Short: sell $4.50, buy back $2.00 → ($4.50 - $2.00) × qty × mult = +profit
- Long expired: buy $5, "sell" at $0 → ($0 - $5) × qty × mult = -loss
- Short expired: "buy" at $0, sold $4.50 → ($4.50 - $0) × qty × mult = +profit

### EXPIRE Handling

- Auto-closes **all** remaining open lots for the contract key (all contracts with the same expiration expire together)
- Close price is **$0**
- Client does not send quantity or pricePerShare — both are auto-calculated/set
- Long lots: P&L = total loss of premium paid
- Short lots: P&L = full premium kept

### EXERCISE Handling

- Closes the **specified quantity** of contracts (partial exercise is valid)
- Close price is **$0** (premium consumed by conversion to shares; P&L plays out through the equity leg)
- Auto-creates a linked equity trade:

| Option Position | Exercise/Assignment | Auto-Created Equity Trade |
|---|---|---|
| Long CALL exercised | You exercise | BUY `qty × multiplier` shares at strike price |
| Long PUT exercised | You exercise | SELL `qty × multiplier` shares at strike price |
| Short CALL assigned | Counterparty exercises | SELL `qty × multiplier` shares at strike price |
| Short PUT assigned | Counterparty exercises | BUY `qty × multiplier` shares at strike price |

- Both the option trade and the auto-created equity trade set `linked_trade_id` pointing to each other (bidirectional)
- The equity trade's `notes` field is auto-populated: e.g., "Auto-created from CALL exercise on AAPL $200C 5/16/26"

### Covered Call Lifecycle

Full example with the system:

```
1. BUY 100 shares AAPL @ $200          → equity trade (existing functionality)
2. SELL 1 contract AAPL $210C 5/16/26 @ $4.50  → option trade (sell-first = short)
3a. BUY 1 contract AAPL $210C 5/16/26 @ $2.00  → closes short, P&L = +$250
    — OR —
3b. EXPIRE                              → closes short at $0, P&L = +$450 (keep full premium)
    — OR —
3c. EXERCISE (assigned)                 → closes short at $0, P&L = +$450
                                          + auto-creates SELL 100 shares AAPL @ $210
                                          (equity SELL matches against the $200 BUY lot → +$1000 equity P&L)
```

## API / OpenAPI Changes

### `LogTradeRequest`

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
      default: EQUITY
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

**Required fields reduced to `[ticker, side]`.** Remaining fields validated in service layer by context:

| Side | assetType | quantity | pricePerShare | Option fields |
|---|---|---|---|---|
| BUY/SELL | EQUITY | required, > 0 | required, > 0 | must be null |
| BUY/SELL | OPTION | required, > 0 | required, > 0 | all required |
| EXPIRE | OPTION | ignored (auto-calc) | ignored (auto $0) | all required (contract identity) |
| EXERCISE | OPTION | required, > 0 | ignored (auto $0) | all required (contract identity) |
| EXPIRE/EXERCISE | EQUITY | rejected | rejected | N/A |

### `Trade` Response

Add fields: `assetType`, `optionType`, `strikePrice`, `expirationDate`, `multiplier`, `linkedTradeId`.

### `ClosedTrade` Response

Add fields: `assetType`, `optionType`, `strikePrice`, `expirationDate`.

### `GET /api/trades` Filter

Side query parameter enum expands to `[BUY, SELL, EXPIRE, EXERCISE]`.

### No New Endpoints

All existing endpoints work as-is with richer data. Stats remain unified across equity and option trades.

## Frontend Changes

### Log Trade Modal

**Asset type toggle** at the top: `[EQUITY] [OPTION]` — EQUITY is the default. Selecting OPTION reveals additional fields.

**Side buttons split into two rows for OPTION mode:**

```
Side:      [BUY] [SELL]                    ← primary actions
Resolve:   [EXPIRE] [EXERCISE]             ← secondary row, muted --text-muted style
```

EQUITY mode shows only the BUY/SELL row (unchanged from current behavior).

**Option-specific fields** (shown only when OPTION selected):

- **Type:** `[CALL] [PUT]` toggle buttons
- **Strike ($):** number input
- **Expiration:** date input

**Contextual labels and visibility:**

- "Quantity" label → "Contracts" when OPTION selected
- "Price / Share ($)" label → "Premium ($)" when OPTION selected
- **Total cost hint** below premium field: `1 × $4.50 × 100 = $450.00`
- EXPIRE: hides quantity and premium fields (auto-calculated)
- EXERCISE: hides premium field (auto $0), keeps quantity ("Contracts") visible

### Closed Trades Table

**Sub-line pattern** for option contract details (no extra "Asset" column):

```
Ticker         | Qty | Buy     | Sell     | P&L     | Held
AAPL           | 30  | $185.20 | $195.00  | +$294   | 45d
AAPL           | 1   | $4.50   | $2.00    | -$250   | 12d
 $210 Call 5/16/26                 ← sub-line in --text-muted
```

- Equity rows: single line, unchanged
- Option rows: primary line with ticker + numbers, sub-line with `$strike Type M/D/YY`
- Expiration year included in compact display: `5/16/26`

**$0 price display:** When price is $0 due to expiration or exercise, display `$0 EXP` or `$0 EXER` instead of plain `$0.00`.

### Stats

Unified across equities and options. No separate filtering for now.

## Demo Seed Data

Add 3 option trades to `DemoService.seedTrades()` alongside existing equity trades:

1. **Covered call (bought back):** SELL 1 AAPL $200 CALL exp 4/18/26 @ $6.20, then BUY back @ $3.50 (+$270 profit). Demonstrates the most common use case.
2. **Profitable long call:** BUY 1 NVDA $130 CALL exp 3/21/26 @ $4.80, SELL @ $8.50 (+$370 profit).
3. **Expired put (loss):** BUY 1 TSLA $220 PUT exp 3/21/26 @ $3.25, EXPIRE ($-325 loss). Demonstrates expiration flow.

## Testing Strategy

### Unit Tests (`DefaultTradeServiceTest`)

- Long option BUY → SELL P&L with ×100 multiplier
- Short option (sell first → buy back) P&L
- EXPIRE long (full loss of premium)
- EXPIRE short (keep full premium)
- EXERCISE long CALL → verify linked equity BUY created at strike, correct share quantity
- EXERCISE short CALL (assigned) → verify linked equity SELL created at strike
- Mixed equity + option stats aggregation
- Validation: reject option fields on equity, reject missing option fields on option
- Validation: reject EXPIRE/EXERCISE on equity trades
- Bidirectional lot matching with partial fills

### Integration Tests (`TradeIT`)

- Log option trade via API, retrieve, verify all fields persisted
- EXPIRE flow end-to-end (verify quantity auto-calculated from open lots)
- EXERCISE flow end-to-end (verify linked trade exists, bidirectional `linked_trade_id`)
- Existing equity tests still pass unchanged (backward compatibility)

## Migration Notes

- Flyway V6 migration must work on both H2 (dev/test) and PostgreSQL (prod)
- The `DROP CONSTRAINT IF EXISTS` syntax may need H2-compatible alternative
- Existing equity data is unaffected — all new columns have safe defaults or are nullable
