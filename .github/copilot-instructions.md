## Quick context for AI contributors

This monorepo centers on a Spring Boot API: `apps/api/trader-assistant/trading-dashboard`.
Primary language: Java 21. Build: Gradle (use the included `./gradlew` in the service root).

Keep these priorities in mind: correctness → tests → clarity → performance. Follow the OpenAPI contract; prefer implementing generated interfaces rather than handcrafting endpoint signatures.

## Where to start
- Read the PRD: `docs/PRD.md` and architecture: `docs/ARCHITECTURE.md` for scope and boundaries.
- Open the service root and use the wrapper: `cd apps/api/trader-assistant/trading-dashboard && ./gradlew bootRun` to run locally.
- Codegen: if you change `docs/openapi.yaml`, run `./gradlew openApiGenerate` from the same directory.

## Important files and paths
- API & app code: `apps/api/trader-assistant/trading-dashboard/src/main/java/...` (packages under `com.austinharlan.trading_dashboard`).
- Config & migrations: `src/main/resources/application.properties|application.yml` and `src/main/resources/db/migration` (Flyway).
- OpenAPI spec: `docs/openapi.yaml` (source of DTOs and generated interfaces).
- Examples: `http/` (curl-like `.http` files) — update samples when you add/change endpoints.
- Docs that govern behavior: `docs/AI_README.md`, `docs/AGENTS.md`, and `docs/ARCHITECTURE.md`.

## Coding conventions specific to this repo
- Layering: controller → service → provider/repository. Keep domain logic out of controllers.
- DTOs: generated from OpenAPI. Do not hand-edit generated DTOs; map internal models using MapStruct or dedicated mappers.
- Transactions belong in the service layer only; repositories are thin data access layers.
- Tests: unit tests are `*Test.java`, integration tests `*IT.java`. Integration tests may use Testcontainers (Postgres).
- Formatting: Spotless + Google Java Format. Use `./gradlew spotlessApply` to format and `./gradlew spotlessCheck` to verify.

## Commands you will use frequently (service root)
- Run locally: `./gradlew bootRun`
- Build & test: `./gradlew clean build`
- Run unit & integration tests: `./gradlew test` (use `-Dspring.profiles.active=test` for test profile)
- Codegen after OpenAPI edits: `./gradlew openApiGenerate`
- Lint/format & security scan: `./gradlew spotlessCheck test dependencyCheckAnalyze`

## Implementation checklist for adding or changing an endpoint
1. Update `docs/openapi.yaml` with the new contract or change.
2. Run `./gradlew openApiGenerate` to regenerate interfaces/DTOs.
3. Implement the generated controller interface or service (put business logic in `service` layer).
4. Add unit tests for services (`*Test`) and integration tests (`*IT`) when DB or full context is required.
5. Update or add `.http` example in `http/` showing expected request/response.
6. Run `./gradlew clean build` and ensure Spotless passes and no dependency warnings.

## Things to avoid
- Do not place domain/business logic in controllers.
- Do not commit secrets — see `ENV.example` for required env vars.
- Do not bypass OpenAPI generation; maintain the spec as the source of truth.

## Observability & runtime notes
- Actuator endpoints are available (health/metrics). Use `localhost:8080/actuator/health` when running locally.
- Use `application-test` profile for integration tests (Testcontainers Postgres).

## Quick pointers for reviewers
- Look for adherence to layering and that new behavior has unit + integration tests.
- Confirm OpenAPI is updated and codegen was run if public API changed.
- Check `spotlessCheck` and `dependencyCheckAnalyze` results as part of the PR.

If anything in this doc is unclear or you want more examples (for example, a concrete code snippet mapping OpenAPI-generated DTO → domain model), tell me which area to expand and I will iterate.
