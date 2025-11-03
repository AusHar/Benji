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
+- Support multiple upstream providers with failover and rate-limit handling.
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
+  - Search-driven navigation and simple interaction patterns mirroring OpenAI aesthetics.
+- Provide future hooks for conversational assistants (e.g., `/api/assistant` endpoint).
+
+### 4.5 Non-Functional Requirements
+- Deployable via CI/CD to AWS ECS with zero-downtime rolling updates.
+- Secrets sourced from AWS SSM/Secrets Manager; no secrets stored in code or repo.
+- Observability through Spring Boot Actuator, structured logging, and trace IDs.
+- Automated testing (unit, integration, contract) must pass before release.
+- System must gracefully degrade when providers are unavailable (serve cached data, partial responses).
+
+## 5. Scope & Phasing
+
+### Phase 0 – Foundation (Complete)
+- Spring Boot service with quote endpoint, caching, provider abstraction, CI/CD scaffold, documentation stubs.
+
+### Phase 1 – Data Depth (Current)
+- Expand OpenAPI contract for quotes, portfolio, and finance modules.
+- Implement production-ready AlphaVantage provider integration with retries and failover.
+- Add persistence models and migrations for portfolio/finance data.
+- Author PRD and runbook (this document) to finalize requirements.
+
+### Phase 2 – Dashboard MVP
+- Ship UI consuming the API (web client or future mobile).
+- Implement portfolio ingestion, expense tracking endpoints, and analytics calculations.
+- Harden security (API key enforcement, actuator credentials) and document configuration.
+
+### Phase 3 – Production Hardening
+- Add monitoring/alerting, synthetic checks, automated backups, and disaster recovery plan.
+- Support role-based access if additional users are onboarded.
+- Optimize cost/performance; consider distributed cache if scaling beyond a single ECS task.
+
+## 6. Dependencies & Assumptions
+- External market data from AlphaVantage (primary) and secondary provider TBD.
+- Postgres database available for persistence; Testcontainers for integration testing.
+- AWS infrastructure (ECR, ECS, IAM roles, SSM) provisioned separately.
+- UI implementation to follow, consuming this API; early releases may use command-line or API clients.
+
+## 7. Risks & Mitigations
+- **Provider rate limits:** Use caching, exponential backoff, and alternate providers.
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
+Additional flows for portfolio ingestion and expense tracking will be documented as those features mature.
