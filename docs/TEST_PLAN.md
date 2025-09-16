

# Test Plan & Conventions

> Goal: catch regressions early, prove behavior matches the PRD and OpenAPI, and keep refactors safe.

## Scope & Priorities
1) **Services (unit)** – business rules, edge cases, null/empty handling.
2) **Controllers (integration)** – request/response shapes match OpenAPI; error mapping.
3) **Repositories (integration)** – DB queries behave with realistic data.
4) **Contracts** – generated DTOs align with `docs/openapi.yaml`.

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
```bash
make test       # unit tests
make it         # integration tests (uses test profile)
make build      # full build
```

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
```java
@SpringBootTest
@Testcontainers
class PortfolioRepositoryIT {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired PortfolioRepository repo;

    @Test
    void shouldPersistAndLoadPosition() {
        var p = new PositionEntity("AAPL", new BigDecimal("10"));
        repo.save(p);
        assertThat(repo.findBySymbol("AAPL")).isPresent();
    }
}
```

## HTTP Examples Directory
- Add `.http` files under `/http/` mirroring endpoints (Codex reuses them):
```
http/quotes.http
GET http://localhost:8080/quotes/AAPL
Accept: application/json
```

## CI Guidance (optional, later)
- Run `make check` on PRs; fail on test/lint errors
- Upload JUnit XML; keep flaky tests out of main

## When Adding/Changing an Endpoint
1) Update `docs/openapi.yaml` (add `operationId`, responses, Error schema)
2) `make codegen`
3) Implement interface in controller
4) Add/adjust unit + integration tests
5) Add/refresh `/http/*.http` example

---
**Definition of Done for tests**: behavior matches PRD + OpenAPI, tests are deterministic, and meaningful assertions protect the public contract.