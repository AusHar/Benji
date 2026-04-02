# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

Monorepo. The only active service lives at `apps/api/trader-assistant/trading-dashboard` (referred to below as the **service root**). All Gradle commands must be run from that directory.

## Commands

All commands run from `apps/api/trader-assistant/trading-dashboard`:

```bash
./gradlew bootRun                      # Run locally (default: dev profile)
./gradlew build                        # Compile + test + assemble JAR
./gradlew spotlessApply                # Auto-format (Google Java Format)
./gradlew spotlessCheck                # Verify formatting (what CI runs)
./gradlew openApiGenerate              # Regenerate DTOs/interfaces from openAPI.yaml
./gradlew test                         # Run all tests
./gradlew test --tests "*.QuoteControllerTest"   # Run a single test class
./gradlew test --tests "*.QuoteControllerTest.methodName"  # Run a single test method
```

CI runs `./gradlew spotlessCheck build --no-daemon`. OWASP dependency scanning runs separately on a weekly schedule (`security.yml`) and is not part of the main pipeline.

## Architecture

**Request flow:** HTTP → Spring Security filter chain → `ApiKeyAuthFilter` (sets `UserContext`) → Controller → Service → Provider/Repository

**OpenAPI-first:** `openAPI.yaml` (service root) is the source of truth for all REST contracts. DTOs and controller interfaces are generated into `build/generated/openapi/` via the `openApiGenerate` task. Never hand-edit generated code. When changing an endpoint, update `openAPI.yaml` first, then run `openApiGenerate`.

**Multi-tenancy:** All data is scoped by `user_id`. `UserContext` (a record in `config/`) holds the current user's identity and is set by `ApiKeyAuthFilter` (prod) or `DevUserFilter` (dev). Access it via `UserContext.current()`. Users table seeded by Flyway migration V5. `AdminController` creates new users (admin-only). Demo user has isolated data resettable via `POST /api/demo/session`.

**Packages under `com.austinharlan.trading_dashboard`:**
- `controllers/` — thin adapters implementing generated interfaces; no business logic. Includes `QuoteController`, `PortfolioController`, `FinanceController`, `TradeController`, `JournalController`, `DemoController`, `AdminController`, `ApiExceptionHandler`
- `service/` — all domain/business logic; `DefaultQuoteService`, `DefaultFinanceInsightsService`, `DefaultTradeService`, `DefaultJournalService`, `DefaultPortfolioService`, `DemoService`
- `marketdata/` — `MarketDataProvider` interface with `RealMarketDataProvider` (Yahoo Finance) and `FakeMarketDataProvider` (dev/test); `YahooCrumbProvider` manages cookie/crumb authentication; `MarketDataHealthIndicator`
- `persistence/` — JPA repositories and entities (`UserEntity`, `TradeEntity`, `JournalEntryEntity`, `JournalGoalEntity`, `FinanceTransactionEntity`, `PortfolioPositionEntity`); Flyway migrations in `src/main/resources/db/migration/` (V1–V5)
- `finance/` and `portfolio/` — domain record types
- `trades/` — `ClosedTrade` record type for P&L calculations
- `config/` — `ActuatorSecurityConfig`, `DevSecurityConfig`, `ApiKeyAuthFilter`, `DevUserFilter`, `DevDataSeeder`, `UserContext`, `UserRepository`, `CorsConfig`, `ProdSecretsValidator`, `ApiSecurityProperties`, `MarketDataProperties`

**Profiles:**
| Profile | DB | Auth | Notes |
|---|---|---|---|
| `dev` (default) | H2 in-memory, Flyway disabled | All requests permitted (`DevSecurityConfig`) | `FakeMarketDataProvider`; `DevUserFilter` auto-sets user context; `DevDataSeeder` creates dev + demo users |
| `test` | H2 in-memory, Flyway enabled | `test-actuator-password` | Integration tests use Testcontainers Postgres which overrides datasource at runtime; `demo.cooldown-ms=0` disables rate limiting |
| `prod` | PostgreSQL, Flyway enabled | API key + actuator basic auth | `ProdSecretsValidator` rejects placeholder secrets on startup |

**Security (non-dev profiles):** `ApiKeyAuthFilter` looks up the API key in `UserRepository` and populates `UserContext` in the `SecurityContextHolder`. Actuator endpoints use HTTP Basic auth (separate `SecurityFilterChain` at `HIGHEST_PRECEDENCE`). Public routes (no auth required): `/actuator/health`, Swagger UI, the static frontend, and `POST /api/demo/session`. The demo endpoint has a configurable rate limiter (`demo.cooldown-ms`, default 5000ms).

**Tests:** `*Test.java` = unit tests (no Spring context or mocks only). `*IT.java` = integration tests extending `DatabaseIntegrationTest`, which spins up a Postgres Testcontainers container. Integration tests are skipped locally when Docker is unavailable (`disabledWithoutDocker = true`) but run in CI.

**Frontend:** Single-file SPA at `src/main/resources/static/index.html`, served as a static resource from the JAR. No build step, no frameworks — vanilla HTML/CSS/JS only. Features: dashboard, quotes (with price chart + news), portfolio, finance, journal, and trade pages. Includes a market opening animation on login (CSS transitions + SVG line-draw) and a continuous ticker tape (shared `fillTickerTrack()` helper). Terminal dark theme (IBM Plex Mono, `#4edd8a` green accent).

## Deployment

Deployed to AWS Lightsail (Ubuntu 22.04, `107.22.236.28`) as a systemd service. nginx reverse-proxies to port 8080 with Let's Encrypt TLS at `https://port.adhdquants.com`.

CI (`ci.yml`) builds the JAR, backs up the previous JAR on the server, rsync's the new one, restarts the `benji` systemd unit, and polls health at `https://port.adhdquants.com/actuator/health` (20 attempts × 15s). On health check failure, CI automatically rolls back to the previous JAR. Pushes to `main` auto-deploy.

## Environment variables

Copy `ENV.example` to populate required variables. `MANAGEMENT_PASSWORD` has no default and must always be set (even in test, via `src/test/resources/application.properties`). In prod, `ProdSecretsValidator` will refuse to start if `TRADING_API_KEY`, `SPRING_DATASOURCE_PASSWORD`, or `MANAGEMENT_PASSWORD` are blank or placeholder values. No market data API key is required — Yahoo Finance is unauthenticated.

## Conventions

- Business logic belongs in `service/`, not controllers.
- All data access must be scoped by `user_id` — use `UserContext.current().userId()` in services.
- Spring Data JPA derived delete methods (e.g., `deleteAllByUserId`) require explicit `@Transactional` — unlike built-in `JpaRepository` methods which get it from `SimpleJpaRepository`.
- Spotless (Google Java Format) is enforced in CI — run `spotlessApply` before committing.
- `MANAGEMENT_PASSWORD` has no fallback in `application.yml`; tests that don't use `@ActiveProfiles("test")` rely on `src/test/resources/application.properties` to supply it.
- Do not commit `Co-Authored-By: Claude` lines in commit messages.
