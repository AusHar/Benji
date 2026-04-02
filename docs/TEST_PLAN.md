

# Test Plan & Conventions

> Goal: catch regressions early, prove behavior matches the PRD and OpenAPI, and keep refactors safe.

## Scope & Priorities
1) **Services (unit)** – business rules, edge cases, null/empty handling (quotes, trades, journal, portfolio, finance, demo).
2) **Controllers (integration)** – request/response shapes match OpenAPI; error mapping.
3) **Repositories (integration)** – DB queries behave with realistic data; multi-tenant isolation verified.
4) **Contracts** – generated DTOs align with `apps/api/trader-assistant/trading-dashboard/openAPI.yaml`.
5) **Multi-tenancy** – data isolation between users (covered by `MultiTenancyIT`).

## Tools
- **JUnit 5**, **Mockito** for unit tests
- **Spring Boot Test**, **MockMvc**/**WebTestClient** for HTTP integration
- **Testcontainers (Postgres)** for DB-backed tests
- **AssertJ** for fluent assertions

## Project Conventions
- **Packages**
  - Unit tests mirror main packages: `src/test/java/...`
  - Integration tests end with `*IT.java`
- **Naming**
  - Unit tests: `ClassNameTest`
  - Integration: `ClassNameIT`
  - Test methods: `shouldDoX_whenY`
- **Given/When/Then** comments inside tests
- **No randomness**; if needed, seed it.
- **One assertion focus per test** (allow grouped assertions with AssertJ `assertThat` / `assertAll`).

## Coverage Targets (local, non-gated)
- Services: **≥ 70%** lines
- Controllers: cover **happy path + 2 error paths** per endpoint
- Repos: at least **one** query round-trip per repository

## What must be tested (checklist)
- **DTO validation**: `@NotNull`, `@Size`, invalid/empty inputs → 400
- **Error mapping**: domain exceptions → correct HTTP (via `@ControllerAdvice`)
- **Boundary cases**: min/max string lengths, symbol regex, empty lists
- **Time handling**: UTC `Instant`; no local timezone assumptions
- **Null tolerance**: service guards and preconditions

## Running Tests

All commands run from `apps/api/trader-assistant/trading-dashboard`:

```bash
./gradlew test                                        # all tests (unit + integration)
./gradlew test --tests "*.QuoteControllerTest"        # single test class
./gradlew test --tests "*.TradeIT.shouldLogTrade"     # single test method
./gradlew spotlessCheck build --no-daemon             # full CI build (format check + tests)
```

Integration tests (`*IT.java`) use Testcontainers Postgres and require Docker. They are skipped locally when Docker is unavailable (`disabledWithoutDocker = true`) but always run in CI.

## Unit Test Example (Service)
```java
@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {
    @Mock QuoteProvider provider;
    @InjectMocks QuoteService service;

    @Test
    void shouldReturnQuote_whenSymbolValid() {
        when(provider.fetch("AAPL")).thenReturn(new Quote("AAPL", new BigDecimal("123.45"), Instant.parse("2025-01-01T00:00:00Z")));
        var out = service.getQuote("AAPL");
        assertThat(out.getSymbol()).isEqualTo("AAPL");
        assertThat(out.getPrice()).isEqualByComparingTo("123.45");
    }

    @Test
    void shouldThrow_onBlankSymbol() {
        assertThatThrownBy(() -> service.getQuote(" "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

## Controller Integration Example (HTTP, no DB)
```java
@WebMvcTest(controllers = QuoteController.class)
class QuoteControllerIT {
    @Autowired MockMvc mvc;
    @MockBean QuoteService service;

    @Test
    void shouldReturn200_withQuote() throws Exception {
        when(service.getQuote("AAPL"))
            .thenReturn(new QuoteDTO().symbol("AAPL").price(new BigDecimal("123.45")).ts(Instant.parse("2025-01-01T00:00:00Z")));

        mvc.perform(get("/quotes/AAPL").accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.symbol").value("AAPL"))
           .andExpect(jsonPath("$.price").value(123.45));
    }
}
```

## Repository Integration Example (with Postgres)

Integration tests extend `DatabaseIntegrationTest`, which manages a shared Testcontainers Postgres container and applies Flyway migrations:

```java
class PortfolioPositionRepositoryIT extends DatabaseIntegrationTest {

    @Autowired PortfolioPositionRepository repo;

    @Test
    void shouldPersistAndLoadPosition() {
        var p = new PortfolioPositionEntity(testUserId, "AAPL", new BigDecimal("10"), new BigDecimal("1500"));
        repo.save(p);
        assertThat(repo.findByUserIdAndTicker(testUserId, "AAPL")).isPresent();
    }
}
```

The `DatabaseIntegrationTest` base class provides `testUserId` and handles Testcontainers lifecycle. Use `@ActiveProfiles("test")` for the test profile.

## CI Guidance
- CI runs `./gradlew spotlessCheck build --no-daemon` on every push and PR.
- Test reports and spotless reports are uploaded as GitHub Actions artifacts.
- Integration tests always run in CI (Docker available in GitHub Actions runners).

## When Adding/Changing an Endpoint
1) Update `apps/api/trader-assistant/trading-dashboard/openAPI.yaml` (add `operationId`, responses, Error schema)
2) `./gradlew openApiGenerate`
3) Implement interface in controller
4) Add/adjust unit + integration tests
5) Ensure all data access is scoped by `userId` for multi-tenancy

---
**Definition of Done for tests**: behavior matches PRD + OpenAPI, tests are deterministic, and meaningful assertions protect the public contract.