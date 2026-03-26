# Multi-Tenancy + Demo Mode Design

**Date:** 2026-03-25
**Status:** Draft

## Overview

Add lightweight multi-tenancy (one API key per user) and a frictionless demo mode to the trading dashboard. Demo users click "Try Demo" on the login screen — no key required — and get a pre-seeded portfolio, trades, transactions, and journal. Data resets on each demo session start. Regular users (you + friends) continue using personal API keys. An admin endpoint allows creating new users via curl.

## Data Model

### New: `users` table

| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL | Primary key |
| api_key | VARCHAR(64) | UNIQUE NOT NULL — used for auth lookup |
| display_name | VARCHAR(100) | NOT NULL — shown in UI (future) |
| is_demo | BOOLEAN | DEFAULT FALSE — identifies demo user for reset |
| is_admin | BOOLEAN | DEFAULT FALSE — gates admin endpoints |
| created_at | TIMESTAMP | DEFAULT NOW() |

### Modified: all existing tables

Add `user_id BIGINT NOT NULL REFERENCES users(id)` to:
- `portfolio_position`
- `finance_transaction`
- `journal_entries`
- `journal_goals`
- `trades`

### Migration strategy (V5)

1. Create `users` table
2. Insert the owner user with `api_key = 'owner-api-key-change-me'`, `is_admin = true`, `is_demo = false`
3. Insert the demo user with `api_key = 'demo'`, `is_admin = false`, `is_demo = true`
4. Add `user_id` column to each table as nullable
5. Backfill all existing rows with the owner user's ID
6. Alter `user_id` to NOT NULL
7. Add foreign key constraints and indexes on `user_id` for all tables
8. Drop existing single-column unique constraints that must become user-scoped:
   - `portfolio_position`: DROP `uq_portfolio_position_ticker`, ADD `UNIQUE(user_id, ticker)`
   - `journal_entries`: DROP `uq_journal_entries_entry_date`, ADD `UNIQUE(user_id, entry_date)`
9. Add index on `trades(user_id, ticker)` for user-scoped trade lookups

**Migration portability:** The migration uses a fixed placeholder API key (`'owner-api-key-change-me'`) — no env var dependency. A `@PostConstruct` component (`ApiKeyInitializer`, active in `@Profile("prod")`) updates the owner user's key to `${TRADING_API_KEY}` on startup if it doesn't match. If the owner user row is not found in the `users` table (e.g., V5 migration failed or was skipped), `ApiKeyInitializer` must throw `IllegalStateException` to fail fast — preventing the app from starting with no valid owner and silently rejecting all API key auth. In dev profile (H2, Flyway disabled), the `DevDataSeeder` handles user creation via JPA instead.

**Cascade behavior:** Deleting a `users` row does NOT cascade to child tables. Demo data cleanup is handled explicitly by `DemoService.resetDemoData()`, which deletes from each table individually in the correct order (child tables first). This prevents accidental data loss from a stray user deletion.

## Backend Architecture

### Security Layer

**`ApiKeyAuthFilter` (modified):**
- Instead of comparing against a single `TRADING_API_KEY` env var, looks up the provided key in the `users` table via `UserRepository.findByApiKey(key)`
- On match: creates a `PreAuthenticatedAuthenticationToken` with a `UserContext` as the principal
- On no match: returns 401 as today
- The `isEnabled()` guard in `shouldNotFilter` is removed — the filter is now always active in non-dev profiles. Key validation happens via DB lookup, not env var comparison, so `ApiSecurityProperties.isEnabled()` is no longer relevant
- `shouldNotFilter` whitelist adds `/api/demo/session` alongside existing public paths
- `ActuatorSecurityConfig.applicationSecurity()`: remove the `if (apiSecurityProperties.isEnabled())` branch entirely. The chain always builds the restricted path with `permitAll` for public endpoints. Add `/api/demo/session` to the `requestMatchers(...).permitAll()` list alongside `/`, `/index.html`, `/swagger-ui/**`, etc.
- **Timing-safe comparison tradeoff:** The current constant-time `MessageDigest.isEqual` check is replaced by a DB lookup. Query time variance makes timing attacks theoretically possible but impractical at this scale. This is an accepted tradeoff.

**`UserContext` (new):**
- Record: `UserContext(long userId, String displayName, boolean isDemo, boolean isAdmin)`
- Static helper: `UserContext.current()` reads from `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`
- Used by all services to get the current user's ID without threading parameters

**`DevSecurityConfig` (modified):**
- In dev profile, a `@PostConstruct` component (`DevDataSeeder`) ensures a default dev user exists in the DB via `UserRepository`. The dev user is inserted with `api_key = 'dev'`, `display_name = 'Dev User'`, `is_admin = true`, `is_demo = false`
- A new `DevUserFilter` (a `OncePerRequestFilter` active only in `@Profile("dev")`) runs on every request. It calls `userRepository.findByApiKey("dev")` to look up the dev user, creates a `PreAuthenticatedAuthenticationToken` with a `UserContext` principal, and sets it on the `SecurityContextHolder` — exactly mirroring what the production `ApiKeyAuthFilter` does after a successful key lookup
- This ensures `UserContext.current()` works identically in dev and prod, preventing NPEs when services call `UserContext.current().userId()`
- The `DevSecurityConfig.devSecurity()` chain adds this filter before `AnonymousAuthenticationFilter` while keeping `permitAll()` authorization

### Service Layer

All services that touch the database read `UserContext.current().userId()` and pass it to repository queries. Affected services:
- `DefaultPortfolioService`
- `DefaultFinanceInsightsService`
- `DefaultJournalService`
- `DefaultTradeService`

Repository interfaces updated with user-scoped queries:
- `findAllByUserId(long userId)`
- `findByUserIdAndTicker(long userId, String ticker)`
- Etc.

### New Components

**`UserEntity` + `UserRepository`:**
- JPA entity mapped to `users` table
- Repository with `findByApiKey(String apiKey)`, `findByIsDemo(boolean isDemo)`

**`DemoService`:**
- `resetDemoData()`: wraps in a `@Transactional` block. Deletes all data for the demo user across all tables in FK-safe order (journal_entry_tickers/tags → journal_entries → journal_goals → trades → finance_transaction → portfolio_position), then inserts seed data
- Called by `DemoController` on each demo session start
- Seed data detailed in the "Demo Seed Data" section below

**`DemoController`:**
- `POST /api/demo/session` — public endpoint (no auth required)
- Calls `DemoService.resetDemoData()`
- Returns `{"apiKey": "demo"}` with status 200
- Added to OpenAPI spec

**`AdminController`:**
- `POST /api/admin/users` — requires `is_admin = true` on the requesting user
- Accepts `{"displayName": "Jake"}`, generates a UUID API key
- Returns `{"displayName": "Jake", "apiKey": "generated-uuid"}`
- Non-admin requests get 403 Forbidden
- Added to OpenAPI spec

### What Stays the Same

- `MarketDataProvider` — no user context. Yahoo Finance works identically for all users
- `RealMarketDataProvider` / `FakeMarketDataProvider` — unchanged
- Controller layer — thin adapters, mostly unchanged. User identity comes from `UserContext`, not request parameters
- All existing API contracts — request/response shapes unchanged, just scoped to the authenticated user

## Demo Seed Data

**Seed data independence:** Portfolio positions and trades are seeded as independent tables. The portfolio positions represent the demo user's *current* holdings; the trades represent their *historical* activity. The buy-side trades for held tickers intentionally match the portfolio position quantities and costs, but there is no foreign key or runtime dependency between the two tables — they are populated separately by `DemoService`.

### Portfolio Positions (10)

| Ticker | Sector | Shares | Avg Cost |
|--------|--------|--------|----------|
| NVDA | Semiconductors | 25 | $118.50 |
| OKLO | Nuclear Energy | 100 | $23.75 |
| MRVL | Semiconductors | 50 | $72.30 |
| AAPL | Consumer Tech | 30 | $185.20 |
| MSFT | Enterprise Tech | 15 | $415.60 |
| AMZN | E-Commerce/Cloud | 20 | $186.40 |
| LLY | Pharma | 8 | $790.00 |
| JPM | Financials | 25 | $198.50 |
| COST | Consumer Staples | 10 | $875.30 |
| PLTR | Defense/AI | 75 | $42.15 |

### Watchlist (6) — seeded via frontend localStorage

`TSLA`, `GOOG`, `META`, `AMD`, `SOFI`, `COIN`

### Trades (~15, over past 3 months)

Mix of buys and sells to produce closed trades with P&L via FIFO matching:

| Date | Ticker | Side | Qty | Price | Notes |
|------|--------|------|-----|-------|-------|
| 2026-01-06 | NVDA | BUY | 25 | $118.50 | Initial position |
| 2026-01-06 | AAPL | BUY | 30 | $185.20 | Core holding |
| 2026-01-08 | MRVL | BUY | 50 | $72.30 | Semiconductor play |
| 2026-01-10 | MSFT | BUY | 15 | $415.60 | Cloud/AI thesis |
| 2026-01-13 | PLTR | BUY | 75 | $42.15 | Defense/AI |
| 2026-01-15 | OKLO | BUY | 100 | $23.75 | Nuclear energy bet |
| 2026-01-22 | AMZN | BUY | 20 | $186.40 | AWS growth |
| 2026-01-27 | JPM | BUY | 25 | $198.50 | Financials exposure |
| 2026-02-03 | LLY | BUY | 8 | $790.00 | Pharma/GLP-1 |
| 2026-02-10 | COST | BUY | 10 | $875.30 | Consumer staples |
| 2026-02-14 | SOFI | BUY | 200 | $11.25 | Swing trade |
| 2026-02-28 | SOFI | SELL | 200 | $13.80 | +$510 winner |
| 2026-03-03 | TSLA | BUY | 15 | $245.00 | Bounce play |
| 2026-03-10 | TSLA | SELL | 15 | $232.50 | -$187.50 cut loss |
| 2026-03-17 | AMD | BUY | 40 | $108.75 | Dip buy |
| 2026-03-21 | AMD | SELL | 40 | $115.20 | +$258 quick flip |

### Finance Transactions (~25, over past 2 months)

Realistic spending across categories:

| Date | Description | Amount | Category |
|------|-------------|--------|----------|
| 2026-02-01 | Spotify Premium | -$10.99 | Subscriptions |
| 2026-02-01 | Netflix | -$15.49 | Subscriptions |
| 2026-02-03 | Whole Foods | -$87.32 | Groceries |
| 2026-02-05 | Shell Gas | -$52.40 | Transportation |
| 2026-02-07 | Chipotle | -$14.25 | Restaurants |
| 2026-02-08 | Amazon - Headphones | -$79.99 | Shopping |
| 2026-02-10 | Kroger | -$63.18 | Groceries |
| 2026-02-12 | Uber Eats | -$28.45 | Restaurants |
| 2026-02-14 | Salary Deposit | +$4,250.00 | Income |
| 2026-02-15 | Rent | -$1,450.00 | Housing |
| 2026-02-17 | Electric Bill | -$89.50 | Utilities |
| 2026-02-19 | Trader Joe's | -$45.67 | Groceries |
| 2026-02-21 | Gym Membership | -$35.00 | Health |
| 2026-02-23 | Target | -$42.88 | Shopping |
| 2026-02-25 | Happy Hour - Brewpub | -$36.50 | Restaurants |
| 2026-02-28 | Salary Deposit | +$4,250.00 | Income |
| 2026-03-01 | Spotify Premium | -$10.99 | Subscriptions |
| 2026-03-01 | Netflix | -$15.49 | Subscriptions |
| 2026-03-03 | Costco | -$156.23 | Groceries |
| 2026-03-05 | Shell Gas | -$48.90 | Transportation |
| 2026-03-07 | Panda Express | -$12.75 | Restaurants |
| 2026-03-10 | Whole Foods | -$71.44 | Groceries |
| 2026-03-12 | AWS Bill | -$23.47 | Subscriptions |
| 2026-03-15 | Rent | -$1,450.00 | Housing |
| 2026-03-18 | Dentist Copay | -$40.00 | Health |

### Journal Entries (4)

**2026-01-06 — "Starting the year"**
- Body: "Opened initial positions today. Heavy on semiconductors (NVDA, MRVL) and added nuclear exposure with OKLO. Thesis: AI capex cycle drives chip demand, nuclear provides baseload for data centers. Setting a 6-month review point."
- Tickers: NVDA, MRVL, OKLO
- Tags: thesis, new-position

**2026-02-14 — "SOFI swing trade"**
- Body: "Entered SOFI at $11.25 for a swing. Fintech has been beaten down but earnings were solid. Target $13-14 range, stop at $10. Small position, willing to lose it."
- Tickers: SOFI
- Tags: swing-trade, entry

**2026-02-28 — "Closed SOFI, trimming risk"**
- Body: "Sold SOFI at $13.80 for +$510. Hit the target zone. Keeping the win rate positive matters more than size. Looking at TSLA for a bounce play next week."
- Tickers: SOFI, TSLA
- Tags: exit, win

**2026-03-10 — "TSLA stop hit"**
- Body: "Cut TSLA at $232.50 for a -$187.50 loss. Bounce thesis didn't play out — macro headwinds too strong. Lesson: don't fight the trend. Moved into AMD on the semiconductor dip instead."
- Tickers: TSLA, AMD
- Tags: exit, loss, lesson

## Frontend Changes

### Login Screen (Layout C — Demo-First with Glowing Ember)

Replace the current overlay with:
1. **Title + subtitle** — "Benji" / "Your personal financial assistant" (unchanged)
2. **Demo hero** — green gradient card with radial glow, `✦ Try Demo` heading, subtitle: "Explore a pre-built portfolio with live market data. No login required."
3. **Divider**
4. **Returning user section** — Glowing Ember (`#e08a4a`) colored:
   - "Already have an API key?" label
   - Input field with blinking orange cursor animation + pulsing border (stronger pulse, alternating between `#e08a4a33` and `#e08a4a88`)
   - "Go →" button in ember color

**"Try Demo" click behavior:**
1. Button shows loading state ("Starting demo...")
2. `POST /api/demo/session` — gets back `{"apiKey": "demo"}`
3. Store key, seed demo watchlist in localStorage, call `launch()`

**API key login behavior:**
- Same as today but the key is per-user now

### Demo Mode Banner

When the current session is a demo user (detected by `KEY === 'demo'` or a flag returned from the session endpoint):
- Persistent slim bar at the very top of the app, above the sidebar/content
- Animated shimmer/gradient (e.g., subtle green-to-transparent sweep)
- Text: `"Demo Mode — changes reset each session"` with a `"Sign in →"` link that shows the login overlay
- Does not interfere with layout — pushes content down slightly

### Watchlist Seeding

- On demo session start, frontend sets `localStorage.setItem('benji_watchlist', JSON.stringify(['TSLA','GOOG','META','AMD','SOFI','COIN']))`
- On sign-out from demo, clear the demo watchlist from localStorage
- Regular users: unchanged behavior

## API Changes (OpenAPI)

### New Endpoints

**`POST /api/demo/session`** (public — no auth)
- Response 200: `{"apiKey": "string"}`

**`POST /api/admin/users`** (admin-only)
- Request: `{"displayName": "string"}`
- Response 201: `{"id": number, "displayName": "string", "apiKey": "string"}`
- Response 403: if requesting user is not admin

### Existing Endpoints

All existing endpoints unchanged in contract. They now implicitly scope to the authenticated user via `UserContext`. No request/response shape changes.

## Testing Strategy

### Unit Tests

- `DemoServiceTest` — verify reset wipes and reseeds all tables for demo user only, verify other users' data is untouched
- `UserContext` — verify reads from security context correctly, verify `current()` throws meaningful error when no context

### Integration Tests

- `DemoIT` — `POST /api/demo/session` returns 200 with API key; verify seed data exists; verify calling again resets (idempotent)
- `AdminIT` — `POST /api/admin/users` with admin key succeeds and returns new user; with non-admin key returns 403; with demo key returns 403
- `MultiTenancyIT` — create two users, insert data for each, verify queries return only the correct user's data (critical — ensures no data leakage between users)
- Update existing `TradeIT` to work with user context

### Not Tested (Manual Verification)

- Frontend login flow (demo button, API key input, animations)
- Demo banner visibility and animation
- Watchlist seeding in localStorage

## Profile Behavior

| Profile | User Creation | Auth | Market Data |
|---------|--------------|------|-------------|
| dev | `DevDataSeeder` auto-creates default user via `@PostConstruct`; `DevUserFilter` populates `UserContext` on every request | Permissive (no key needed), but `UserContext` is always populated | `FakeMarketDataProvider` |
| test | Test users created in test setup; `DatabaseIntegrationTest` base class inserts a test user and configures `TRADING_API_KEY` to match | `ApiKeyAuthFilter` active — `isEnabled()` returns true because the test key is set. Tests send the key in the `X-API-KEY` header. Filter looks up user from DB and populates `UserContext`. | `FakeMarketDataProvider` via Testcontainers |
| prod | Owner + demo users via Flyway V5 migration; `ApiKeyInitializer` syncs owner key from env | `ApiKeyAuthFilter` validates against `users` table and populates `UserContext` | `RealMarketDataProvider` (Yahoo Finance) |

**`ProdSecretsValidator` note:** The placeholder value set includes `"demo"`, which matches the demo user's API key stored in the `users` table. This is safe because `ProdSecretsValidator` only checks the `TRADING_API_KEY` env var (the owner's key), not values in the database. The demo user's key `"demo"` is never used as an env var value.

**Test profile `isEnabled()` change:** Currently `ApiSecurityProperties.isEnabled()` returns `false` when the key is empty, which makes `shouldNotFilter` return `true` for all requests (effectively disabling the filter). After this change, the filter will look up keys from the `users` table instead of comparing against a single env var. The `isEnabled()` check should be removed or repurposed — the filter is now always active in non-dev profiles. Tests must create users in the DB and send valid API keys in headers.

## Migration Checklist

1. Flyway V5 migration: create `users` table, add `user_id` to all tables, backfill
2. `ApiKeyInitializer`: sync owner user's API key from env var on startup
3. Update `ApiKeyAuthFilter` to look up users from DB
4. Add `UserContext` and wire into security context
5. Update all repositories with user-scoped queries
6. Update all services to read from `UserContext`
7. Add `DemoService`, `DemoController`
8. Add `AdminController`
9. Update OpenAPI spec with new endpoints
10. Frontend: new login screen, demo flow, demo banner
11. Update existing tests, add new test classes
