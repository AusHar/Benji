# Repository Guidelines

## Project Structure & Module Organization
- Root workspace with apps under `apps/`.
- API service (Spring Boot, Gradle): `apps/api/trader-assistant/trading-dashboard`.
- Always run ./gradlew commands from the trading-dashboard root, not the monorepo root, unless you intend to work with the entire workspace.
- Source code: `src/main/java/...`; configuration: `src/main/resources/application.properties`.
- Database migrations: `src/main/resources/db/migration`.
- Tests: `src/test/java/...` (unit: `*Test.java`, integration: `*IT.java`).

## Build, Test, and Development Commands
- From `apps/api/trader-assistant/trading-dashboard`:
  - `./gradlew spotlessApply`: Format code with Google Java Format.
  - `./gradlew build`: Compile, run tests, and assemble artifacts.
  - `./gradlew clean build`: Clean and rebuild the project.
  - `./gradlew test`: Run unit and integration tests (JUnit 5/Testcontainers).
  - `./gradlew bootRun`: Start the API locally.
  - `./gradlew dependencyCheckAnalyze`: OWASP dependency vulnerability scan.
- Requires Java 21 toolchain; use the Gradle wrapper (`./gradlew`).

## Coding Style & Naming Conventions
- Formatter: Spotless + Google Java Format (enforced via `spotlessApply`).
- Indentation and imports are auto-managed; do not hand-format.
- Naming: classes `UpperCamelCase`, methods/fields `lowerCamelCase`, constants `UPPER_SNAKE_CASE`.
- Packages follow `com.austinharlan.trading_dashboard...` hierarchy; keep feature code close to its controller/service.

## Testing Guidelines
- Framework: JUnit 5; integration tests may use Testcontainers (Docker required).
- Naming: unit tests `*Test.java`; integration tests `*IT.java`.
- Run: `./gradlew test`; view detailed failures in Gradle output.
- Prefer testing via controller/service layers; mock external providers where possible.
- *Unit tests*: Controllers/services with mocks (`@WebMvcTest`, `@MockBean`).
- *Integration tests*: Full Spring context + Testcontainers (Postgres). Use `application-test.yml` to disable DB auto-config when needed.

## Commit & Pull Request Guidelines
- Prefer Conventional Commits: `feat:`, `fix:`, `docs:`, `ci:`, `chore:`, `build:`, `style:`.
- Keep messages imperative and scoped (e.g., `build: add OWASP dep-check`).
- PRs must include: clear description, linked issue (if exists), test coverage for changes, and local run notes (`bootRun` or curl examples) when touching endpoints.

## Security & Configuration Tips
- Do not commit secrets; externalize via environment or Spring config.
- Use `dependencyCheckAnalyze` before merging to surface vulnerable dependencies.
- Cache settings live under com.austinharlan.trading_dashboard.config—avoid introducing unbounded caches.

## Runtime Profiles
- `dev`: Local development; may use H2 or fake providers.
- `test`: Used in automated tests; disables DB auto-config or uses Testcontainers.
- `prod`: Production configuration; Postgres with Flyway migrations.
