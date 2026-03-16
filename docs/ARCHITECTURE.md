# ARCHITECTURE

## Overview
The **Trading Dashboard** is a Spring Boot API that serves quotes, portfolio data, and assistant features for investing/trading/finance to provide a financial and budgeting dashboard and investing analysis. It follows a layered architecture (API → Service → Providers/Repositories) with swappable market-data providers and optional persistence (Postgres).

## Goals & Non-Goals
- **Goals:** Clear layering, swappable data sources, fast local dev, production-ready security posture, testability, AWS functionality/tooling, cloud ready, cloud cost-efficient.
- **Non-Goals (for v0):** AuthN/AuthZ, multi-tenant RBAC, real trading execution.

## Components
- **API Layer (Controllers):** `com.austinharlan.trading_dashboard.controllers`
  - REST endpoints (Spring MVC) returning JSON DTOs.
- **Service Layer:** `com.austinharlan.trading_dashboard.service`
  - Orchestrates flows, caching, validation, rate limiting.
- **Providers (Market Data):** `com.austinharlan.trading_dashboard.marketdata`
  - `MarketDataProvider` interface with `FakeMarketDataProvider` (dev) and `RealMarketDataProvider` (non-dev, Finnhub-backed).
- **Persistence (optional v0):** `com.austinharlan.trading_dashboard.persistence`
  - Spring Data JPA repositories + Flyway migrations (`src/main/resources/db/migration`).
- **Config:** `com.austinharlan.trading_dashboard.config`
  - Caching (`CaffeineCacheManager`), Jackson, Web/HTTP, Cross-cutting concerns.
- **Tests:** `src/test/java/...`
  - Unit (`*Test.java`) with mocks; Integration (`*IT.java`) with Testcontainers (Postgres).

## Runtime Profiles
- **dev:** H2 / fake providers, verbose logs.
- **test:** Testcontainers Postgres, deterministic data.
- **prod:** Managed Postgres, real providers, Flyway enabled, tighter logging/security.

## Data Flow (Quotes)
1) `GET /api/quotes/{symbol}` → Controller  
2) Controller → `QuoteService#getCached(symbol)`  
3) Service checks cache → hits `MarketDataProvider` if miss  
4) Provider returns domain `Quote` → Service → Controller → JSON

## Error & Observability
- **Errors:** Spring problem details (HTTP 4xx/5xx), centralized exception handlers.
- **Metrics/Health:** Spring Boot Actuator (`/actuator/health`, `/metrics`).
- **Logging:** JSON logs (later), correlation IDs for external API calls.

## Caching & Limits
- **Cache:** Caffeine with per-cache TTLs: quotes (30s), overviews (4h), history (1h), news (15m).
- **Rate Limits:** `MarketDataQuotaTracker` tracks Finnhub calls in a per-minute rolling window (60 calls/min free tier). Usage exposed at `/api/marketdata/quota`. Health indicator returns `UNKNOWN` (HTTP 200) instead of `DOWN` when the quota is exhausted.

## Security (phased)
- v0: No secrets in code; use env/direnv.  
- v1: HTTPS everywhere, request validation, basic input sanitation.  
- v2: Spring Security (JWT), roles/permissions, audit logging.

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
    AppCluster[Boot App Cluster] --> CloudDB[(Managed Postgres)]
    AppCluster --> MarketAPIs[External Market Data]
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