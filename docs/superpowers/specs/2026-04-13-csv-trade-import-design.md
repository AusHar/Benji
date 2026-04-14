# CSV Trade Import Design

**Date:** 2026-04-13
**Status:** Approved for implementation

## Goals

1. **Reduce manual entry** — imported trades replace hand-typing for trades you already made.
2. **Backfill history** — import all historical trades from before Benji existed.
3. **Stay in sync** — re-run imports periodically as new activity accumulates.

## Data Source

Robinhood's account activity CSV export (one file per account). All three account types use the same 9-column format:

```
Activity Date, Process Date, Settle Date, Instrument, Description, Trans Code, Quantity, Price, Amount
```

Three account types: `INDIVIDUAL`, `ROTH_IRA`, `TRADITIONAL_IRA`.

**Important:** Robinhood's CSV export does **not** include crypto or spending activity (stated at the bottom of each export). Crypto is out of scope for this feature.

---

## Schema Changes (V9 Migration)

### `trades` table

```sql
-- Account tagging (nullable; manually-entered trades leave this null)
ALTER TABLE trades ADD COLUMN account VARCHAR(20);

-- Dedup key: SHA-256 hash of composite row identity, nullable
ALTER TABLE trades ADD COLUMN import_dedup_key VARCHAR(64);
CREATE UNIQUE INDEX idx_trades_import_dedup_key
    ON trades(import_dedup_key)
    WHERE import_dedup_key IS NOT NULL;

-- Extend fractional share precision from DECIMAL(12,4) to DECIMAL(16,6)
-- Robinhood DRIP purchases can have up to 6 decimal places (e.g., 0.155754 shares)
ALTER TABLE trades ALTER COLUMN quantity TYPE DECIMAL(16,6);
ALTER TABLE trades ALTER COLUMN price_per_share TYPE DECIMAL(16,6);
```

### `finance_transaction` table

```sql
-- Account tagging (same as trades; imported dividends/interest should know their account)
ALTER TABLE finance_transaction ADD COLUMN account VARCHAR(20);

-- Dedup key for idempotent cash event imports
ALTER TABLE finance_transaction ADD COLUMN import_dedup_key VARCHAR(64);
CREATE UNIQUE INDEX idx_finance_transaction_import_dedup_key
    ON finance_transaction(import_dedup_key)
    WHERE import_dedup_key IS NOT NULL;
```

### Entity changes

Both `TradeEntity` and `FinanceTransactionEntity` need new `account` and `importDedupKey` fields with getters and setters (not added to existing constructors — set via setter after construction to avoid breaking callers).

---

## Trans Code Mapping

### Trade-producing rows → `trades` table

| Trans Code | Side | Asset Type | Notes |
|---|---|---|---|
| `Buy` | `BUY` | `EQUITY` | Includes Dividend Reinvestment buys — import normally |
| `Sell` | `SELL` | `EQUITY` | Includes "Options Assigned" equity legs — import normally |
| `BTO` | `BUY` | `OPTION` | Buy to Open (long position) |
| `STC` | `SELL` | `OPTION` | Sell to Close (close long) |
| `STO` | `SELL` | `OPTION` | Sell to Open (short position) |
| `BTC` | `BUY` | `OPTION` | Buy to Close (close short) |
| `OEXP` | `EXPIRE` | `OPTION` | Quantity from CSV; price = $0 |
| `OASGN` | `EXERCISE` | `OPTION` | Quantity from CSV; price = $0; paired equity row (trans code `Sell`/`Buy`, description contains "Options Assigned") is imported independently as a normal equity trade — no special detection or skipping logic needed |

For `OPTION` rows: `multiplier = 100`. For `EQUITY` rows: `multiplier = 1`. **This must be set explicitly** — the service-layer auto-computation is bypassed for direct repository inserts.

### Cash event rows → `finance_transaction` table

| Trans Code | Category (stored) | Description format |
|---|---|---|
| `CDIV` | `null` | `"{TICKER} Cash Dividend"`, notes = raw CSV description |
| `SLIP` | `null` | `"{TICKER} Stock Lending Income"` |
| `INT` | `null` | `"Interest Payment"` |

Category is intentionally null — the user can categorize imported cash events from the Finance page.

### Skipped rows (not imported)

`ACH`, `XENT_CC`, `MINT`, `MTCH`, `PFIR`, `CFIR`, `FUTSWP`, `MISC` — funding movements, IRA contributions, prediction market settlements, and misc bonuses are not imported.

---

## Parsing Pipeline

### CSV parsing

Use **OpenCSV** (`com.opencsv:opencsv:5.9`). Required because Robinhood's equity descriptions span multiple physical lines within a single quoted field (company name + CUSIP on separate lines). A naive line-by-line reader would break on these. Add to `build.gradle`:

```groovy
implementation("com.opencsv:opencsv:5.9")
```

### Option description parsing

Two regex patterns (applied to the `Description` column for OPTION rows):

**Standard** — applies to `BTO`, `STC`, `STO`, `BTC`:
```
Pattern: ^\S+\s+(\d{1,2}/\d{1,2}/\d{4})\s+(Call|Put)\s+\$(.+)$
Groups:  [1]=expirationDate  [2]=optionType  [3]=strikePrice
Example: "AAPL 3/9/2026 Call $257.50"
```

**Expiration** — applies to `OEXP`:
```
Pattern: ^Option Expiration for \S+\s+(\d{1,2}/\d{1,2}/\d{4})\s+(Call|Put)\s+\$(.+)$
Groups:  [1]=expirationDate  [2]=optionType  [3]=strikePrice
Example: "Option Expiration for RXRX 3/27/2026 Call $3.50"
```

**Assignment** — applies to `OASGN`: same format as standard (e.g., `"RXRX 1/16/2026 Call $4.50"`).

The `Instrument` column always contains the clean ticker — never parse the ticker from the description.

### Amount parsing

```
"$1,799.92"   →  +1799.92   (strip $, strip ,)
"($520.08)"   →  -520.08    (strip $, strip ,, negate if wrapped in parens)
""            →  null       (OEXP and OASGN rows have no price/amount)
```

### Dedup key computation

The dedup key is a SHA-256 hex string computed over all 9 raw CSV columns plus the account type and a **sequence number** within each collision group:

```
base = SHA-256(account | activityDate | processDate | settleDate |
               instrument | description_raw | transCode | rawQuantity |
               rawPrice | rawAmount)

key  = SHA-256(base | "|" | sequenceIndexWithinGroup)
```

The sequence number handles genuinely identical rows (e.g., two BTO fills of the same option contract at the same price on the same day — real example in `Individual Jan1-Apr13.csv`). Rows are grouped by their base hash; rows within a group are numbered 0, 1, 2, ... in the order they appear in the CSV. Re-importing the same CSV regenerates the same sequences → correct `DUPLICATE` classification.

### Row classification

Each parsed row gets one of:

| Action | Meaning |
|---|---|
| `IMPORT_TRADE` | Will be inserted into `trades` |
| `IMPORT_CASH_EVENT` | Will be inserted into `finance_transaction` |
| `SKIP_DUPLICATE` | `import_dedup_key` already exists for this user |
| `SKIP_UNSUPPORTED` | Trans code is not imported (ACH, FUTSWP, etc.) |
| `ERROR` | Failed to parse (malformed option description, invalid date, etc.) |

---

## API Design

All endpoints are protected by the existing `ApiKeyAuthFilter`. Demo users receive `403 Forbidden` — guard in `CsvImportService` via `UserContext.current().isDemo()`.

Add to `openAPI.yaml` first (per convention), then run `openApiGenerate`.

### `POST /api/import/csv/preview`

**Request:** `multipart/form-data`
- `file` (binary) — the Robinhood CSV
- `account` (string, enum: `INDIVIDUAL`, `ROTH_IRA`, `TRADITIONAL_IRA`)

**Response:** `ImportPreviewResponse`
```json
{
  "rows": [
    {
      "rowNumber": 3,
      "activityDate": "2026-03-27",
      "instrument": "RXRX",
      "description": "RXRX 4/2/2026 Put $3.50",
      "transCode": "STC",
      "action": "IMPORT_TRADE",
      "detail": "SELL OPTION · RXRX PUT $3.50 exp 2026-04-02",
      "error": null
    }
  ],
  "summary": {
    "tradesToImport": 12,
    "cashEventsToImport": 3,
    "duplicatesSkipped": 2,
    "unsupportedSkipped": 31,
    "errors": 0
  }
}
```

Nothing is written to the database.

### `POST /api/import/csv/confirm`

**Request:** Same multipart form as preview.

**Commit strategy:** Parse all rows identically to preview. If any row produces an `ERROR` classification, **reject the entire import** — return the error list and write nothing. Only if zero errors: insert all `IMPORT_TRADE` and `IMPORT_CASH_EVENT` rows in a single `@Transactional` block.

**Response:** `ImportConfirmResponse`
```json
{
  "tradesImported": 12,
  "cashEventsImported": 3,
  "duplicatesSkipped": 2,
  "errors": []
}
```

---

## Backend Architecture

### New files

```
controllers/ImportController.java        — thin adapter; implements generated OpenAPI interface
service/ImportService.java               — interface
service/CsvImportService.java            — all parsing, classification, dedup, and persistence
```

### Repository additions

**`TradeRepository`** — add:
```java
@Query("SELECT t.importDedupKey FROM TradeEntity t " +
       "WHERE t.userId = :userId AND t.importDedupKey IS NOT NULL")
List<String> findImportDedupKeysByUserId(@Param("userId") Long userId);
```

**`FinanceTransactionRepository`** — add:
```java
@Query("SELECT t.importDedupKey FROM FinanceTransactionEntity t " +
       "WHERE t.userId = :userId AND t.importDedupKey IS NOT NULL")
List<String> findImportDedupKeysByUserId(@Param("userId") Long userId);
```

Both are called once at the start of preview/confirm to build an in-memory `Set<String>` — O(1) membership checks per row thereafter, not N database round trips.

### Import flow (preview and confirm share the same parse pipeline)

```
1. Parse CSV with OpenCSV (handles embedded newlines in quoted fields)
2. Skip footer rows (empty Instrument + disclaimer text)
3. For each data row:
   a. Classify by Trans Code → TRADE / CASH_EVENT / SKIP_UNSUPPORTED
   b. For TRADE: parse option description if OPTION asset type
      → if parse fails: classify as ERROR with message
   c. Parse Amount (strip $ and commas, negate if parenthesised)
   d. Compute base hash → assign sequence number → compute final dedup key
   e. Check dedup key against pre-loaded Set<String> → SKIP_DUPLICATE if present
4. Preview: return classification list + summary (no writes)
   Confirm: if any ERROR rows → return errors, write nothing
            if zero errors → insert all IMPORT_TRADE + IMPORT_CASH_EVENT in @Transactional
```

### Validation before insert (service-layer bypass)

Since we insert directly into repositories (not through `DefaultTradeService.logTrade()`), the import service must enforce:
- `OPTION` rows: `optionType`, `strikePrice`, `expirationDate` all non-null (parse error if missing)
- `EQUITY` rows: `optionType`, `strikePrice`, `expirationDate` all null
- `quantity > 0` for all trade rows
- `pricePerShare >= 0` for all trade rows (EXPIRE/EXERCISE use $0 per V7 constraint)
- `multiplier = 100` for OPTION, `multiplier = 1` for EQUITY

### `linked_trade_id` on OASGN imports

OASGN (option assignment) rows and their paired equity "Options Assigned" rows are imported as two independent trades without `linked_trade_id` set on either. The link is a UI convenience (not used in P&L computation) and can't be reliably inferred during bulk import. This is a known, acceptable gap.

### `posted_at` for cash events

`Activity Date` (M/D/YYYY `LocalDate`) is converted to `Instant` via `activityDate.atStartOfDay(ZoneOffset.UTC).toInstant()`.

---

## Frontend

### Navigation

New "Import" tab added to the existing tab navigation (bottom on mobile, alongside other tabs on desktop).

### Page states

**State 1 — Upload form**
- Account type dropdown: `Individual`, `Roth IRA`, `Traditional IRA`
- File picker (`.csv` only)
- "Preview Import" button — disabled until both fields populated
- Helper text: *"Upload your Robinhood account activity CSV. Crypto activity is not available in Robinhood exports."*

**State 2 — Preview table**
- Summary bar: `12 trades to import · 3 cash events · 2 duplicates skipped · 0 errors`
- Table: Date | Instrument | Detail | Action badge
- Badge colours: green=TRADE, blue=CASH EVENT, gray=DUPLICATE, yellow=SKIPPED, red=ERROR
- Error rows expand inline to show parse error message
- Buttons: **Back** (resets to State 1) | **Confirm Import** (calls `/confirm`)
- If summary shows 0 errors, Confirm Import is enabled; if errors exist, it is disabled with a message to fix the file

**State 3 — Result**
- Success summary: `✓ 12 trades imported · 3 cash events imported · 2 duplicates skipped`
- Error list if any
- "Import Another File" button resets to State 1

---

## Testing

**Unit tests** (`CsvImportServiceTest.java`):
- Each trans code path (Buy, Sell, BTO, STC, STO, BTC, OEXP, OASGN, CDIV, SLIP, INT, skipped codes)
- Option description regex: standard, expiration, assignment, malformed (→ ERROR)
- Amount parsing: positive, negative (parenthesised), empty
- Dedup key sequence numbering with identical rows
- Demo user guard (expect exception)
- Validation: missing option fields, zero quantity

**Integration test** (`CsvImportIT.java`):
- Upload `Individual Jan1-Apr13.csv` via `/preview` — assert summary counts
- Upload same file via `/confirm` — assert rows in DB, account tags correct
- Re-upload same file via `/confirm` — assert all rows classified `DUPLICATE`, DB count unchanged
- Upload file as demo user — assert 403

---

## Known Limitations

- **No time-of-day ordering:** `findAllChronologicalByUserId` sorts by `trade_date ASC, created_at ASC`. Imported trades share nearly identical `created_at` values (all set to `Instant.now()` at import time). For same-ticker same-day trades in a backfill, sub-day ordering is indeterminate. Robinhood's CSV has no time-of-day column, so this cannot be resolved. Does not affect correctness for different-day trades or different tickers.
- **Crypto not available:** Robinhood explicitly excludes crypto from account activity CSV exports. Crypto import is out of scope.
- **`linked_trade_id` not set on OASGN pairs:** UI convenience link only; P&L is unaffected.
- **Category null on cash events:** Imported CDIV/SLIP/INT rows have null category; user must categorize manually from the Finance page.
