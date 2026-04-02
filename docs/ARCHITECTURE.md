# ARCHITECTURE

## Overview
The **Trading Dashboard** is a Spring Boot API that serves quotes, portfolio data, and assistant features for investing/trading/finance to provide a financial and budgeting dashboard and investing analysis. It follows a layered architecture (API → Service → Providers/Repositories) with swappable market-data providers and optional persistence (Postgres).

## Goals & Non-Goals
- **Goals:** Clear layering, swappable data sources, fast local dev, production-ready security posture, testability, AWS functionality/tooling, cloud ready, cloud cost-efficient.
- **Non-Goals:** Real trading execution, OAuth/OIDC identity federation, public user registration.

## Components
- **API Layer (Controllers):** `com.austinharlan.trading_dashboard.controllers`
  - REST endpoints (Spring MVC) returning JSON DTOs.
  - `QuoteController`, `PortfolioController`, `FinanceController`, `TradeController`, `JournalController`, `DemoController`, `AdminController`
  - `ApiExceptionHandler` centralizes error mapping.
- **Service Layer:** `com.austinharlan.trading_dashboard.service`
  - Orchestrates flows, caching, and validation. All services scope data by `UserContext.current().userId()`.
  - `DefaultQuoteService`, `DefaultFinanceInsightsService`, `DefaultPortfolioService`, `DefaultTradeService`, `DefaultJournalService`, `DemoService`
- **Providers (Market Data):** `com.austinharlan.trading_dashboard.marketdata`
  - `MarketDataProvider` interface with `FakeMarketDataProvider` (dev) and `RealMarketDataProvider` (non-dev, Yahoo Finance). `YahooCrumbProvider` manages cookie/crumb authentication. `MarketDataHealthIndicator` for actuator health.
- **Persistence:** `com.austinharlan.trading_dashboard.persistence`
  - Spring Data JPA repositories + Flyway migrations V1–V5 (`src/main/resources/db/migration`).
  - Entities: `UserEntity`, `TradeEntity`, `JournalEntryEntity`, `JournalGoalEntity`, `FinanceTransactionEntity`, `PortfolioPositionEntity`
  - All tables include a `user_id` foreign key for multi-tenant isolation (added in V5).
- **Domain Records:** `finance/` (`FinanceSummaryData`, `FinanceTransactionRecord`), `portfolio/` (`PortfolioSnapshot`, `PortfolioHolding`), `trades/` (`ClosedTrade`)
- **Config:** `com.austinharlan.trading_dashboard.config`
  - Security: `ActuatorSecurityConfig`, `DevSecurityConfig`, `ApiKeyAuthFilter`, `DevUserFilter`
  - Identity: `UserContext` (record), `UserRepository`, `ApiKeyInitializer`
  - Startup: `DevDataSeeder` (seeds dev/demo users), `ProdSecretsValidator`
  - Caching (`CaffeineCacheManager`), CORS, properties bindings
- **Tests:** `src/test/java/...`
  - Unit (`*Test.java`) with mocks; Integration (`*IT.java`) extending `DatabaseIntegrationTest` which spins up Testcontainers Postgres.

## Runtime Profiles
- **dev:** H2 / fake providers, verbose logs.
- **test:** Testcontainers Postgres, deterministic data.
- **prod:** Managed Postgres, real providers, Flyway enabled, tighter logging/security.

## Data Flow (Quotes)
1) `GET /api/quotes/{symbol}` → Controller  
2) Controller → `QuoteService#getCached(symbol)`  
3) Service checks cache → hits `MarketDataProvider` if miss  
4) Provider returns domain `Quote` → Service → Controller → JSON

## Data Flow (Multi-Tenant Request)
1) HTTP request with `X-API-KEY` header  
2) `ApiKeyAuthFilter` looks up key in `UserRepository` → sets `UserContext` in `SecurityContextHolder`  
3) Controller delegates to Service  
4) Service calls `UserContext.current().userId()` to scope all queries  
5) Repository returns only data belonging to that user

## Data Flow (Demo Mode)
1) `POST /api/demo/session` (no auth required, 5s rate limit)  
2) `DemoController` → `DemoService.resetDemoData()`  
3) Deletes all demo user data (trades, portfolio, finance, journal)  
4) Seeds fresh sample data (10 positions, 16 trades, 25 transactions, 4 journal entries)  
5) Returns `{"apiKey": "demo"}` for frontend to use

## Error & Observability
- **Errors:** Spring problem details (HTTP 4xx/5xx), centralized exception handlers.
- **Metrics/Health:** Spring Boot Actuator (`/actuator/health`, `/metrics`).
- **Logging:** JSON logs (later), correlation IDs for external API calls.

## Caching & Limits
- **Cache:** Caffeine with per-cache TTLs: quotes (30s), overviews (4h), history (1h), news (15m).
- **Upstream Availability:** Yahoo Finance is unauthenticated (no API key). Health indicator returns `UNKNOWN` (HTTP 200) when Yahoo Finance is temporarily unreachable, and `DOWN` only on unexpected application errors.

## Security
- Secrets in systemd service environment; no secrets in code or repo.
- HTTPS via nginx + Let's Encrypt at `https://port.adhdquants.com`.
- **Prod:** `ApiKeyAuthFilter` looks up `X-API-KEY` in `UserRepository`, populates `UserContext`. Actuator endpoints protected by HTTP Basic auth on a separate `SecurityFilterChain` (`HIGHEST_PRECEDENCE`).
- **Dev:** `DevSecurityConfig` permits all requests. `DevUserFilter` auto-sets a dev user context.
- Public routes (all profiles): `/actuator/health`, Swagger UI, static frontend (`/`), `POST /api/demo/session`.
- Demo endpoint rate-limited via `AtomicLong` + configurable cooldown (`demo.cooldown-ms`, default 5000ms).

## Diagrams

### Layered Architecture
```mermaid
flowchart TD
  Client --> C[Controllers]
  C --> S[Services]
  S --> P[MarketDataProvider]
  S --> R[Repositories]
  R --> DB[(Postgres)]
  P --> X[External Market APIs]

flowchart LR
  subgraph Dev
    App[Spring Boot App] --> DevDB[(Docker Postgres or H2)]
  end
  subgraph Prod
    App2[Boot App - Lightsail] --> CloudDB[(Managed Postgres)]
    App2 --> MarketAPIs[Yahoo Finance]
  end

sequenceDiagram
  participant U as User
  participant C as Controller
  participant S as QuoteService
  participant Cache as Caffeine
  participant Prov as MarketDataProvider
  U->>C: GET /api/quotes/UUUU
  C->>S: getCached("UUUU")
  S->>Cache: lookup UUUU
  alt cache hit
    Cache-->>S: Quote
  else miss
    S->>Prov: getQuote("UUUU")
    Prov-->>S: Quote
    S->>Cache: put UUUU→Quote
  end
  S-->>C: Quote
  C-->>U: 200 OK (JSON)