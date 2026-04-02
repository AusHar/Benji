

# AI_README

> Purpose: Tell AI coding assistants exactly how to work inside this repo—what to optimize for, what to avoid, and where to find specs.

## Mission
Build a reliable trading/analysis backend with a thin web UI. Priorities: **correctness → tests → clarity → performance**.

## Source of Truth
- PRD: `docs/PRD.md` (scope, users, success metrics)
- Architecture: `docs/ARCHITECTURE.md` (diagrams, boundaries)
- API Contract: `apps/api/trader-assistant/trading-dashboard/openAPI.yaml` (generate DTOs & interfaces)
- Runbook: `docs/RUNBOOK.md` (ports, health, common failures)

## Tech Stack (authoritative)
- **Language:** Java 21
- **Framework:** Spring Boot 3.x
- **Build:** Gradle (Wrapper only)
- **DB:** PostgreSQL
- **Testing:** JUnit 5, Mockito, Testcontainers (Postgres)
- **Observability:** Micrometer/Prometheus (dev), SLF4J logging

## Conventions & Rules
- **Layering:** `controller → service → repository`. *No* domain logic in controllers.
- **DTOs:** Only at boundaries; generated from OpenAPI. Map internal ↔ external with dedicated mapper methods in services.
- **Errors:** Throw domain exceptions; map to HTTP via `@ControllerAdvice`. Use a standard error body `{ code, message }`.
- **Nulls:** Validate inputs with `jakarta.validation` on DTOs. Fail fast.
- **Transactions:** Service layer only. Keep repos simple. Note: Spring Data JPA derived delete methods (e.g., `deleteAllByUserId`) need explicit `@Transactional`.
- **Multi-tenancy:** All data access must be scoped by `user_id`. Use `UserContext.current().userId()` in services.
- **Time:** Use `Instant` (UTC). No `java.util.Date`.
- **Config:** No secrets in source. Use env vars; see `ENV.example`.
- **Logging:** Key:value, one line. No PII. Include `requestId` when available.
- **Commits/PRs:** Conventional Commits; every endpoint change updates `apps/api/trader-assistant/trading-dashboard/openAPI.yaml` and `/http/` examples.

## Definitions of Done (DoD)
- Unit tests for services (happy + edge cases).
- Integration tests for controllers (Testcontainers Postgres when DB involved).
- OpenAPI spec updated; `./gradlew openApiGenerate` is clean; build is green.
- No TODOs or dead code. Spotless passes.

## Tasks the AI Should Perform
- Implement controller interfaces generated from OpenAPI—*do not* handcraft endpoints.
- Create services and repositories to satisfy PRD-defined behaviors.
- Add/extend tests when altering behavior.
- Update docs if behavior or contracts change.

## Tasks the AI Should NOT Perform
- Do not introduce new frameworks, databases, or cloud services.
- Do not bypass OpenAPI or place domain logic in controllers.
- Do not commit generated code or secrets.

## File Map (important paths)
```
docs/PRD.md
docs/ARCHITECTURE.md
apps/api/trader-assistant/trading-dashboard/openAPI.yaml
src/main/java/...                           # app code
src/test/java/...                           # unit + integration tests
src/main/resources/application.yml          # profile configs (dev/test/prod)
src/main/resources/static/index.html        # single-file SPA (vanilla HTML/CSS/JS)
src/main/resources/db/migration/            # Flyway migrations V1–V5
src/test/resources/application.properties   # test overrides (management password, demo cooldown)
```

## Build & Codegen
- Generate DTOs & API interfaces:
```
./gradlew openApiGenerate
```
- Build & test:
```
./gradlew clean build
```

## Environment Variables (see `ENV.example`)
```
POSTGRES_URL=jdbc:postgresql://localhost:5432/trader
POSTGRES_USER=trader
POSTGRES_PASSWORD=changeme
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080
TRADING_API_KEY=replace_me          # API key for prod auth
MANAGEMENT_PASSWORD=change_me       # Actuator basic auth (required, no default)
```

## Prompting Hints (for AI tools)
- Reference the **PRD** for scope and acceptance criteria.
- Follow **Conventions & Rules** strictly before optimizing performance.
- When adding an endpoint: update `apps/api/trader-assistant/trading-dashboard/openAPI.yaml`, run codegen, implement interface, write tests.
- If something is ambiguous, propose a small ADR in `docs/adr/` and proceed with the simplest option.

## Quality Bar
- 70%+ line coverage on services; meaningful assertions.
- No warnings in IDE/Gradle. Spotless/Checkstyle clean.
- p95 latency for simple GETs < 300ms locally.

## Contacts / Ownership
- CODEOWNERS owns reviews for `src/**` and `docs/**`.

---
**TL;DR for AI**: Obey the OpenAPI contract, keep logic in services, write tests, update docs, and never leak secrets.