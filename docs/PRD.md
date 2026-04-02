+# Product Requirements Document (PRD)
+
+## 1. Product Overview
+Benji is a personal investing and financial health assistant that combines market intelligence with portfolio and budgeting insights. The Spring Boot API delivers quote retrieval, portfolio aggregation, and expense tracking that will power a sleek, minimal UI inspired by OpenAI's design language. The initial release targets a single user (the owner) but will lay the groundwork for future multi-user support.
+
+## 2. Goals & Success Metrics
+- **Unified insight:** Present a consolidated view of market data, portfolio performance, and personal finances in a single dashboard.
+- **Research depth:** Provide quote lookups enriched with Greeks, fundamentals, and sentiment signals from multiple providers.
+- **Financial awareness:** Track brokerage balances (e.g., Robinhood) and personal spending trends with daily granularity.
+- **Operational readiness:** Maintain a reliable production deployment with automated CI/CD, secrets management, and health monitoring.
+
+**Success metrics (v1):**
+- 95% of quote requests served in <500ms for cached symbols; <2s when hitting external providers.
+- Daily portfolio valuation ingested successfully with <1% failure rate.
+- Expense ingestion pipeline up-to-date within 24 hours.
+- Zero critical regressions across automated tests before release.
+
+## 3. User Personas
+- **Primary user (current):** Project owner acting as power user, analyst, and administrator.
+- **Future expansion:** Public release may introduce read-only consumers, authenticated collaborators, and automated bots; these are out of scope for v1 but inform security and scaling considerations.
+
+## 4. Core Features & Requirements
+
+### 4.1 Quote Intelligence
+- Retrieve quotes by ticker symbol with aggregated metrics (price, volume, Greeks, basic financials, sentiment).
+- Support upstream provider abstraction with caching and graceful degradation.
+- Cache frequent lookups with configurable TTL; expose freshness metadata to clients.
+- Log provider latency and errors for observability.
+
+### 4.2 Portfolio Aggregation
+- Ingest balances and positions from brokerage exports (initially Robinhood CSV/API) and normalize holdings into a unified schema.
+- Track cost basis, realized/unrealized gains, and performance over time.
+- Provide summary endpoints for total account value, allocation breakdown, and risk indicators.
+
+### 4.3 Personal Finance Tracking
+- Import transactions from personal banking/credit accounts (CSV to start) and categorize spend.
+- Surface burn-rate analytics, budget adherence, and monthly trends.
+- Allow manual adjustments and annotations for edge cases.
+
+### 4.4 Dashboard & Assistant Interface
+- Deliver data to a UI featuring:
+  - Overview panels for portfolio value, cash position, and recent spend.
+  - Research workspace combining quote analytics with DD notes.
+  - Terminal dark aesthetic: IBM Plex Mono, neon green accent, interactive watchlist tile system with expand/collapse, fundamentals, and sparklines.
+- Provide future hooks for conversational assistants (e.g., `/api/assistant` endpoint).
+
+### 4.5 Non-Functional Requirements
+- Deployable via CI/CD to AWS Lightsail with automated rsync + systemd restart.
+- Secrets sourced from systemd service environment; no secrets stored in code or repo.
+- Observability through Spring Boot Actuator, structured logging, and trace IDs.
+- Automated testing (unit, integration, contract) must pass before release.
+- System must gracefully degrade when providers are unavailable (serve cached data, partial responses).
+
+## 5. Scope & Phasing
+
+### Phase 0 – Foundation (Complete)
+- Spring Boot service with quote endpoint, caching, provider abstraction, CI/CD scaffold, documentation stubs.
+
+### Phase 1 – Data Depth (Complete)
+- OpenAPI contract for quotes, portfolio, and finance modules.
+- Yahoo Finance provider integration with cookie/crumb authentication.
+- Persistence models and Flyway migrations for portfolio/finance data.
+
+### Phase 2 – Dashboard MVP (Complete)
+- Single-file SPA (terminal dark theme): watchlist tiles, stat cards, quotes, portfolio, finance pages.
+- Portfolio ingestion, expense tracking endpoints, and analytics calculations.
+- API key auth enforcement, actuator credentials, and production deployment on AWS Lightsail.
+
+### Phase 3 – Production Hardening (Complete)
+- Live sparkline data (1D/5D/3M candle history via Yahoo Finance).
+- News feed per ticker in expanded watchlist tiles.
+- Monitoring/alerting, synthetic checks, and disaster recovery plan.
+
+### Phase 4 – Multi-Tenancy, Trade Tracking & Journal (Complete)
+- Multi-tenancy: `user_id` column on all tables (Flyway V5), `UserContext` identity propagation, `ApiKeyAuthFilter` user lookup.
+- Demo mode: isolated demo user with resettable sample data, public `POST /api/demo/session` with rate limiting.
+- Admin user creation: `POST /api/admin/users` (admin-only).
+- Trade tracking: log BUY/SELL trades, closed-trade P&L matching, stats (win rate, streaks, top tickers), P&L history, trade calendar.
+- Trading journal: freeform entries with auto-extracted $tickers and #tags, goals with progress tracking, streak/heatmap stats.
+- Terminal dark redesign: IBM Plex Mono, neon green accent, interactive watchlist tiles with sparklines.
+
+### Phase 5 – Stability Hardening & UI Polish (Complete)
+- Frontend bug fixes: journal position loading, chart/news DOM race, demo sign-out cleanup, input validation.
+- Build hardening: disabled plain JAR, CI rollback on failed health check.
+- Backend fixes: `@Transactional` on derived delete queries, portable JPQL, demo rate limiting.
+- Test reliability: scoped test cleanup, duplicate test removal.
+- UI polish: market opening animation (CSS transitions + SVG line-draw), unified continuous ticker tape, accessibility improvements (color contrast, touch targets, landmarks).
+
+### Phase 6 – Current
+- Historical price intelligence and optimal timing analysis.
+- Enhanced portfolio analytics and risk indicators.
+
+## 6. Dependencies & Assumptions
+- External market data from Yahoo Finance (no API key required).
+- Postgres database available for persistence; Testcontainers for integration testing.
+- AWS Lightsail instance provisioned; CI deploys via rsync + systemd.
+- UI: single-file SPA served from the JAR, currently shipped.
+
+## 7. Risks & Mitigations
+- **Provider availability:** Use caching and graceful degradation when Yahoo Finance is unreachable.
+- **Data accuracy:** Validate ingestion pipelines, add reconciliation jobs against broker balances.
+- **Security gaps:** Enforce API keys in prod, rotate secrets, and audit access logs.
+- **Operational drift:** Maintain runbook, automated smoke tests, and infrastructure-as-code to reduce manual variance.
+
+## 8. User Flows
+```mermaid
+flowchart TD
+  User((User)) -->|Search ticker| QuotesAPI[GET /api/quotes/{symbol}]
+  QuotesAPI --> Cache[Caffeine Cache]
+  Cache -->|Hit| RespondQuotes[Return enriched quote]
+  Cache -->|Miss| Provider[Market Data Provider]
+  Provider --> RespondQuotes
+  RespondQuotes --> DashboardUI[Dashboard UI]
+```
+
+### Demo Mode Flow
+```mermaid
+flowchart TD
+  Visitor((Visitor)) -->|Try Demo| DemoAPI[POST /api/demo/session]
+  DemoAPI -->|Rate limit check| DemoService[DemoService.resetDemoData]
+  DemoService -->|Delete + reseed| DB[(Postgres)]
+  DemoService -->|Return apiKey: demo| Visitor
+  Visitor -->|Use demo key| Dashboard[Dashboard UI]
+```
+
+### Trade Tracking Flow
+```mermaid
+flowchart TD
+  User((User)) -->|Log trade| TradeAPI[POST /api/trades]
+  TradeAPI --> TradeService[TradeService]
+  TradeService -->|Scoped by userId| DB[(Postgres)]
+  User -->|View stats| StatsAPI[GET /api/trades/stats]
+  StatsAPI --> TradeService
+  TradeService -->|Match closed trades| PnL[P&L Calculation]
+```
