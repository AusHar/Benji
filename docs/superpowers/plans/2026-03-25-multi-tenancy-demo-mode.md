# Multi-Tenancy + Demo Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add lightweight multi-tenancy (one API key per user) and a frictionless demo mode with pre-seeded data to the trading dashboard.

**Architecture:** New `users` table with `user_id` FK on all existing tables. `ApiKeyAuthFilter` does DB lookups instead of env var comparison. `UserContext` record (populated from SecurityContext) provides current user identity to all services. Demo mode resets/seeds data via `POST /api/demo/session`. Admin can create users via `POST /api/admin/users`.

**Tech Stack:** Spring Boot 3, Spring Security, JPA/Hibernate, Flyway, PostgreSQL (Testcontainers for tests), H2 (dev), single-file SPA frontend.

**Spec:** `docs/superpowers/specs/2026-03-25-multi-tenancy-demo-mode-design.md`

**Path shorthands used throughout:**
- `$ROOT` = `apps/api/trader-assistant/trading-dashboard`
- `$SRC` = `$ROOT/src/main/java/com/austinharlan/trading_dashboard`
- `$TEST` = `$ROOT/src/test/java/com/austinharlan/trading_dashboard`
- `$RES` = `$ROOT/src/main/resources`

---

## Task 1: V5 Flyway Migration

**Files:**
- Create: `$RES/db/migration/V5__multi_tenancy.sql`

- [ ] **Step 1: Write the migration**

```sql
-- V5__multi_tenancy.sql
-- Multi-tenancy: users table + user_id FK on all existing tables

-- 1. Create users table
CREATE TABLE users (
    id           BIGSERIAL    PRIMARY KEY,
    api_key      VARCHAR(64)  NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    is_demo      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_admin     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 2. Seed owner and demo users
INSERT INTO users (api_key, display_name, is_admin, is_demo)
VALUES ('owner-api-key-change-me', 'Owner', TRUE, FALSE);

INSERT INTO users (api_key, display_name, is_admin, is_demo)
VALUES ('demo', 'Demo User', FALSE, TRUE);

-- 3. portfolio_position: add user_id, replace UNIQUE(ticker) with UNIQUE(user_id, ticker)
ALTER TABLE portfolio_position ADD COLUMN user_id BIGINT;
UPDATE portfolio_position SET user_id = (SELECT id FROM users WHERE is_admin = TRUE);
ALTER TABLE portfolio_position ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE portfolio_position
    ADD CONSTRAINT fk_portfolio_position_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_portfolio_position_user_id ON portfolio_position(user_id);
ALTER TABLE portfolio_position DROP CONSTRAINT uq_portfolio_position_ticker;
ALTER TABLE portfolio_position
    ADD CONSTRAINT uq_portfolio_position_user_ticker UNIQUE (user_id, ticker);

-- 4. finance_transaction: add user_id
ALTER TABLE finance_transaction ADD COLUMN user_id BIGINT;
UPDATE finance_transaction SET user_id = (SELECT id FROM users WHERE is_admin = TRUE);
ALTER TABLE finance_transaction ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE finance_transaction
    ADD CONSTRAINT fk_finance_transaction_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_finance_transaction_user_id ON finance_transaction(user_id);

-- 5. journal_entries: add user_id, replace UNIQUE(entry_date) with UNIQUE(user_id, entry_date)
ALTER TABLE journal_entries ADD COLUMN user_id BIGINT;
UPDATE journal_entries SET user_id = (SELECT id FROM users WHERE is_admin = TRUE);
ALTER TABLE journal_entries ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE journal_entries
    ADD CONSTRAINT fk_journal_entries_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_journal_entries_user_id ON journal_entries(user_id);
ALTER TABLE journal_entries DROP CONSTRAINT uq_journal_entries_entry_date;
ALTER TABLE journal_entries
    ADD CONSTRAINT uq_journal_entries_user_entry_date UNIQUE (user_id, entry_date);

-- 6. journal_goals: add user_id
ALTER TABLE journal_goals ADD COLUMN user_id BIGINT;
UPDATE journal_goals SET user_id = (SELECT id FROM users WHERE is_admin = TRUE);
ALTER TABLE journal_goals ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE journal_goals
    ADD CONSTRAINT fk_journal_goals_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_journal_goals_user_id ON journal_goals(user_id);

-- 7. trades: add user_id with composite index
ALTER TABLE trades ADD COLUMN user_id BIGINT;
UPDATE trades SET user_id = (SELECT id FROM users WHERE is_admin = TRUE);
ALTER TABLE trades ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE trades
    ADD CONSTRAINT fk_trades_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_trades_user_id_ticker ON trades(user_id, ticker);
```

- [ ] **Step 2: Verify migration syntax**

Run: `cd $ROOT && ./gradlew build -x test 2>&1 | tail -20`

This won't fully validate yet (entities not updated), but confirms the file is picked up.

- [ ] **Step 3: Commit**

```bash
git add $RES/db/migration/V5__multi_tenancy.sql
git commit -m "feat: add V5 migration for multi-tenancy (users table + user_id FKs)"
```

---

## Task 2: UserEntity + UserRepository

**Files:**
- Create: `$SRC/persistence/UserEntity.java`
- Create: `$SRC/persistence/UserRepository.java`

- [ ] **Step 1: Create UserEntity**

```java
package com.austinharlan.trading_dashboard.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "users")
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "api_key", nullable = false, unique = true, length = 64)
  private String apiKey;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(name = "is_demo", nullable = false)
  private boolean demo;

  @Column(name = "is_admin", nullable = false)
  private boolean admin;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected UserEntity() {}

  public UserEntity(String apiKey, String displayName, boolean admin, boolean demo) {
    this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
    this.displayName = Objects.requireNonNull(displayName, "displayName");
    this.admin = admin;
    this.demo = demo;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isDemo() {
    return demo;
  }

  public boolean isAdmin() {
    return admin;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
```

- [ ] **Step 2: Create UserRepository**

```java
package com.austinharlan.trading_dashboard.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByApiKey(String apiKey);

  Optional<UserEntity> findByIsDemoTrue();

  Optional<UserEntity> findByAdminTrueAndDemoFalse();
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd $ROOT && ./gradlew compileJava 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add $SRC/persistence/UserEntity.java $SRC/persistence/UserRepository.java
git commit -m "feat: add UserEntity and UserRepository"
```

---

## Task 3: UserContext Record + Unit Test

**Files:**
- Create: `$SRC/config/UserContext.java`
- Create: `$TEST/config/UserContextTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.austinharlan.trading_dashboard.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class UserContextTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void current_returnsUserContext_whenPrincipalIsSet() {
    var ctx = new UserContext(42L, "Alice", false, true);
    var auth = new PreAuthenticatedAuthenticationToken(ctx, "", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    UserContext result = UserContext.current();

    assertThat(result.userId()).isEqualTo(42L);
    assertThat(result.displayName()).isEqualTo("Alice");
    assertThat(result.isDemo()).isFalse();
    assertThat(result.isAdmin()).isTrue();
  }

  @Test
  void current_throwsIllegalState_whenNoAuthentication() {
    assertThatThrownBy(UserContext::current)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No UserContext");
  }

  @Test
  void current_throwsIllegalState_whenPrincipalIsWrongType() {
    var auth = new PreAuthenticatedAuthenticationToken("not-a-user-context", "", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThatThrownBy(UserContext::current)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No UserContext");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd $ROOT && ./gradlew test --tests "*.UserContextTest" 2>&1 | tail -10`
Expected: FAIL — `UserContext` class does not exist

- [ ] **Step 3: Write UserContext implementation**

```java
package com.austinharlan.trading_dashboard.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public record UserContext(long userId, String displayName, boolean isDemo, boolean isAdmin) {

  public static UserContext current() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof UserContext ctx)) {
      throw new IllegalStateException(
          "No UserContext in SecurityContext — ensure request passed through auth filter");
    }
    return ctx;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd $ROOT && ./gradlew test --tests "*.UserContextTest" 2>&1 | tail -10`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add $SRC/config/UserContext.java $TEST/config/UserContextTest.java
git commit -m "feat: add UserContext record with SecurityContext integration"
```

---

## Task 4: ApiKeyInitializer

**Files:**
- Create: `$SRC/config/ApiKeyInitializer.java`

This component runs on startup in prod profile. It reads `TRADING_API_KEY` from the environment and updates the owner user's key in the DB to match.

- [ ] **Step 1: Create ApiKeyInitializer**

```java
package com.austinharlan.trading_dashboard.config;

import com.austinharlan.trading_dashboard.persistence.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
class ApiKeyInitializer {

  private final UserRepository userRepository;
  private final ApiSecurityProperties apiSecurityProperties;

  ApiKeyInitializer(UserRepository userRepository, ApiSecurityProperties apiSecurityProperties) {
    this.userRepository = userRepository;
    this.apiSecurityProperties = apiSecurityProperties;
  }

  @PostConstruct
  void syncOwnerApiKey() {
    String envKey = apiSecurityProperties.getKey();
    // Look up by role, not by placeholder key — the key changes after first startup
    var ownerUser =
        userRepository.findByAdminTrueAndDemoFalse()
            .orElseThrow(() -> new IllegalStateException(
                "PROD-REQUIRED: Owner user not found in users table. "
                    + "Ensure V5 migration has run successfully."));
    if (!envKey.equals(ownerUser.getApiKey())) {
      ownerUser.setApiKey(envKey);
      userRepository.save(ownerUser);
    }
  }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd $ROOT && ./gradlew compileJava 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add $SRC/config/ApiKeyInitializer.java
git commit -m "feat: add ApiKeyInitializer to sync owner API key from env on startup"
```

---

## Task 5: Modify ApiKeyAuthFilter + ActuatorSecurityConfig

> **WARNING: Tests will be broken from this task until Task 13 completes.** After this task, `ApiKeyAuthFilter` does DB lookups and existing entity constructors have changed (Task 7). Do NOT run `./gradlew test` between Tasks 5 and 13. Only run `./gradlew compileJava` to verify compilation.

**Files:**
- Modify: `$SRC/config/ApiKeyAuthFilter.java`
- Modify: `$SRC/config/ActuatorSecurityConfig.java`
- Modify: `$SRC/config/ApiSecurityProperties.java` (delete `isEnabled()`)

The filter changes from single-key env var comparison to DB lookup via `UserRepository`. The security config removes the `isEnabled()` branch and adds `/api/demo/session` to permitAll.

- [ ] **Step 1: Modify ApiKeyAuthFilter**

Replace the entire class. Key changes:
- Inject `UserRepository` instead of just `ApiSecurityProperties`
- `shouldNotFilter`: remove `isEnabled()` check, add `/api/demo/session`
- `doFilterInternal`: DB lookup via `userRepository.findByApiKey()`, create `UserContext` principal
- Remove `isKeyValid()` method (no longer needed)

```java
package com.austinharlan.trading_dashboard.config;

import com.austinharlan.trading_dashboard.persistence.UserEntity;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("!dev")
class ApiKeyAuthFilter extends OncePerRequestFilter {

  private final ApiSecurityProperties properties;
  private final UserRepository userRepository;

  ApiKeyAuthFilter(ApiSecurityProperties properties, UserRepository userRepository) {
    this.properties = properties;
    this.userRepository = userRepository;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri.equals("/")
        || uri.equals("/index.html")
        || uri.startsWith("/actuator/")
        || uri.startsWith("/swagger-ui")
        || uri.startsWith("/v3/api-docs")
        || uri.equals("/api/demo/session");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String providedKey = request.getHeader(properties.getHeaderName());
    if (!StringUtils.hasText(providedKey)) {
      sendUnauthorized(response);
      return;
    }

    Optional<UserEntity> user = userRepository.findByApiKey(providedKey);
    if (user.isEmpty()) {
      sendUnauthorized(response);
      return;
    }

    UserEntity u = user.get();
    UserContext ctx = new UserContext(u.getId(), u.getDisplayName(), u.isDemo(), u.isAdmin());
    PreAuthenticatedAuthenticationToken authentication =
        new PreAuthenticatedAuthenticationToken(ctx, "", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(authentication);
    try {
      filterChain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void sendUnauthorized(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response
        .getOutputStream()
        .write("{\"error\":\"invalid_api_key\"}".getBytes(StandardCharsets.UTF_8));
  }
}
```

- [ ] **Step 2: Modify ActuatorSecurityConfig — remove isEnabled() branch, add /api/demo/session**

The `applicationSecurity` method should always build the restricted chain. Add `/api/demo/session` to permitAll:

```java
// Replace the applicationSecurity method body:
@Bean
@Order(Ordered.LOWEST_PRECEDENCE)
SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception {
  http.authorizeHttpRequests(
          auth ->
              auth.requestMatchers(
                      "/",
                      "/index.html",
                      "/api/demo/session",
                      "/swagger-ui/**",
                      "/swagger-ui.html",
                      "/v3/api-docs/**")
                  .permitAll()
                  .anyRequest()
                  .authenticated())
      .addFilterBefore(apiKeyAuthFilter, AnonymousAuthenticationFilter.class);

  http.cors(Customizer.withDefaults())
      .httpBasic(AbstractHttpConfigurer::disable)
      .formLogin(AbstractHttpConfigurer::disable)
      .csrf(AbstractHttpConfigurer::disable);
  return http.build();
}
```

Also remove the `ApiSecurityProperties` field from the class (no longer needed for `isEnabled()` check). The constructor only needs `ApiKeyAuthFilter`.

- [ ] **Step 3: Remove isEnabled() from ApiSecurityProperties**

Read `$SRC/config/ApiSecurityProperties.java` and delete the `isEnabled()` method. Keep `getKey()` and `getHeaderName()`.

- [ ] **Step 3b: Verify ProdSecretsValidator is unaffected**

Read `$SRC/config/ProdSecretsValidator.java`. Confirm it uses `apiSecurityProperties.getKey()` (not `isEnabled()`). No changes needed — `ProdSecretsValidator` only checks the `TRADING_API_KEY` env var, not DB values. The `"demo"` entry in `PLACEHOLDER_VALUES` is safe because it applies to the env var, not to the demo user's DB key.

- [ ] **Step 4: Verify compilation**

Run: `cd $ROOT && ./gradlew compileJava 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add $SRC/config/ApiKeyAuthFilter.java $SRC/config/ActuatorSecurityConfig.java $SRC/config/ApiSecurityProperties.java
git commit -m "feat: ApiKeyAuthFilter does DB lookup, remove isEnabled() guard"
```

---

## Task 6: DevDataSeeder + DevUserFilter

**Files:**
- Create: `$SRC/config/DevDataSeeder.java`
- Create: `$SRC/config/DevUserFilter.java`
- Modify: `$SRC/config/DevSecurityConfig.java`

In dev profile (Flyway disabled, H2), we need: (1) a seeder to create users via JPA, (2) a filter to populate `UserContext` on every request.

- [ ] **Step 1: Create DevDataSeeder**

```java
package com.austinharlan.trading_dashboard.config;

import com.austinharlan.trading_dashboard.persistence.UserEntity;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
class DevDataSeeder {

  private final UserRepository userRepository;

  DevDataSeeder(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @PostConstruct
  void seed() {
    if (userRepository.findByApiKey("dev").isEmpty()) {
      userRepository.save(new UserEntity("dev", "Dev User", true, false));
    }
    if (userRepository.findByApiKey("demo").isEmpty()) {
      userRepository.save(new UserEntity("demo", "Demo User", false, true));
    }
  }
}
```

- [ ] **Step 2: Create DevUserFilter**

```java
package com.austinharlan.trading_dashboard.config;

import com.austinharlan.trading_dashboard.persistence.UserEntity;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("dev")
class DevUserFilter extends OncePerRequestFilter {

  private final UserRepository userRepository;
  private volatile UserContext cachedCtx;

  DevUserFilter(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (cachedCtx == null) {
      UserEntity devUser = userRepository.findByApiKey("dev")
          .orElseThrow(() -> new IllegalStateException("Dev user not found"));
      cachedCtx = new UserContext(devUser.getId(), devUser.getDisplayName(), devUser.isDemo(), devUser.isAdmin());
    }
    PreAuthenticatedAuthenticationToken auth =
        new PreAuthenticatedAuthenticationToken(cachedCtx, "", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);
    try {
      filterChain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
```

- [ ] **Step 3: Modify DevSecurityConfig to wire the filter**

```java
package com.austinharlan.trading_dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@Profile("dev")
class DevSecurityConfig {

  private final DevUserFilter devUserFilter;

  DevSecurityConfig(DevUserFilter devUserFilter) {
    this.devUserFilter = devUserFilter;
  }

  @Bean
  SecurityFilterChain devSecurity(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .addFilterBefore(devUserFilter, AnonymousAuthenticationFilter.class)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd $ROOT && ./gradlew compileJava 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add $SRC/config/DevDataSeeder.java $SRC/config/DevUserFilter.java $SRC/config/DevSecurityConfig.java
git commit -m "feat: add DevDataSeeder and DevUserFilter for dev profile UserContext"
```

---

## Task 7: Add user_id to All Entities + Update Repositories

**Files:**
- Modify: `$SRC/persistence/PortfolioPositionEntity.java`
- Modify: `$SRC/persistence/FinanceTransactionEntity.java`
- Modify: `$SRC/persistence/JournalEntryEntity.java`
- Modify: `$SRC/persistence/JournalGoalEntity.java`
- Modify: `$SRC/persistence/TradeEntity.java`
- Modify: `$SRC/persistence/PortfolioPositionRepository.java`
- Modify: `$SRC/persistence/FinanceTransactionRepository.java`
- Modify: `$SRC/persistence/JournalEntryRepository.java`
- Modify: `$SRC/persistence/JournalGoalRepository.java`
- Modify: `$SRC/persistence/TradeRepository.java`

- [ ] **Step 1: Add user_id field to all 5 entities**

Add this field and update constructors for each entity. Pattern (same for all):

```java
@Column(name = "user_id", nullable = false)
private Long userId;
```

**PortfolioPositionEntity** — also update `@Table` annotation:
```java
@Table(
    name = "portfolio_position",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "ticker"})})
```
Add `userId` as first constructor parameter: `public PortfolioPositionEntity(Long userId, String ticker, BigDecimal qty, BigDecimal basis)`. Add getter `public Long getUserId()`.

**FinanceTransactionEntity** — add `userId` field and update both constructors. The auto-UUID constructor becomes:
```java
public FinanceTransactionEntity(
    Long userId, Instant postedAt, String description, BigDecimal amount, String category, String notes) {
  this(UUID.randomUUID().toString(), userId, postedAt, description, amount, category, notes);
}
```
The explicit-ID constructor becomes:
```java
public FinanceTransactionEntity(
    String id, Long userId, Instant postedAt, String description, BigDecimal amount, String category, String notes) {
  this.id = Objects.requireNonNull(id, "id must not be null");
  this.userId = Objects.requireNonNull(userId, "userId must not be null");
  // ... rest unchanged
}
```
Add getter `public Long getUserId()`. Update all existing callers of the old constructors (search codebase for `new FinanceTransactionEntity(`).

**JournalEntryEntity** — add `userId` field. The constructor becomes:
```java
public JournalEntryEntity(Long userId, String body, LocalDate entryDate, Set<String> tickers, Set<String> tags) {
  this.userId = Objects.requireNonNull(userId, "userId must not be null");
  this.body = Objects.requireNonNull(body, "body must not be null");
  // ... rest unchanged
}
```
Add getter `public Long getUserId()`. **Important:** `getTickers()` and `getTags()` return `Collections.unmodifiableSet(...)`. To set tickers/tags after construction, use `setTickers(Set)` and `setTags(Set)`, not `getTickers().add()`.

**JournalGoalEntity** — add `userId` to constructor. Add getter.

**TradeEntity** — add `userId` to constructor. Add getter.

- [ ] **Step 2: Update PortfolioPositionRepository**

```java
package com.austinharlan.trading_dashboard.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPositionEntity, Long> {

  // findByTicker REMOVED — cross-user data leak without user_id scope

  List<PortfolioPositionEntity> findAllByUserId(Long userId);

  Optional<PortfolioPositionEntity> findByUserIdAndTicker(Long userId, String ticker);

  void deleteByUserIdAndTicker(Long userId, String ticker);

  void deleteAllByUserId(Long userId);
}
```

**Note:** Delete `findByTicker(String ticker)`. All callers must use `findByUserIdAndTicker` instead. Search for `findByTicker` across the codebase and update.

- [ ] **Step 3: Update TradeRepository**

Add user-scoped versions of all queries. Remove old non-user-scoped methods (`findAllByOrderByTradeDateDescCreatedAtDesc`, `findFiltered`, `findAllChronological`) — they become dead code and are cross-user data leak risks. Add:

```java
List<TradeEntity> findAllByUserIdOrderByTradeDateDescCreatedAtDesc(Long userId);

@Query("""
    SELECT t FROM TradeEntity t
    WHERE t.userId = :userId
      AND (:ticker IS NULL OR t.ticker = :ticker)
      AND (:side IS NULL OR t.side = :side)
      AND (:fromDate IS NULL OR t.tradeDate >= :fromDate)
      AND (:toDate IS NULL OR t.tradeDate <= :toDate)
    ORDER BY t.tradeDate DESC, t.createdAt DESC
    """)
List<TradeEntity> findFilteredByUserId(
    @Param("userId") Long userId,
    @Param("ticker") String ticker,
    @Param("side") String side,
    @Param("fromDate") LocalDate fromDate,
    @Param("toDate") LocalDate toDate);

@Query("SELECT t FROM TradeEntity t WHERE t.userId = :userId ORDER BY t.tradeDate ASC, t.createdAt ASC")
List<TradeEntity> findAllChronologicalByUserId(@Param("userId") Long userId);

void deleteAllByUserId(Long userId);
```

- [ ] **Step 4: Update JournalEntryRepository**

Add user-scoped queries:

```java
List<JournalEntryEntity> findAllByUserIdOrderByEntryDateDesc(Long userId);

Optional<JournalEntryEntity> findByUserIdAndEntryDate(Long userId, LocalDate entryDate);

@Query("SELECT e FROM JournalEntryEntity e JOIN e.tickers t WHERE t = :ticker AND e.userId = :userId ORDER BY e.entryDate DESC")
List<JournalEntryEntity> findByUserIdAndTicker(@Param("userId") Long userId, @Param("ticker") String ticker);

@Query("SELECT e FROM JournalEntryEntity e JOIN e.tags t WHERE t = :tag AND e.userId = :userId ORDER BY e.entryDate DESC")
List<JournalEntryEntity> findByUserIdAndTag(@Param("userId") Long userId, @Param("tag") String tag);

@Query("SELECT e.entryDate FROM JournalEntryEntity e WHERE e.userId = :userId ORDER BY e.entryDate ASC")
List<LocalDate> findAllEntryDatesAscByUserId(@Param("userId") Long userId);

@Query("SELECT t, COUNT(e) FROM JournalEntryEntity e JOIN e.tickers t WHERE e.userId = :userId GROUP BY t ORDER BY COUNT(e) DESC")
List<Object[]> countByTickerAndUserId(@Param("userId") Long userId);

@Query("SELECT t, COUNT(e) FROM JournalEntryEntity e JOIN e.tags t WHERE e.userId = :userId GROUP BY t ORDER BY COUNT(e) DESC")
List<Object[]> countByTagAndUserId(@Param("userId") Long userId);

@Query("SELECT COUNT(DISTINCT e.entryDate) FROM JournalEntryEntity e WHERE e.userId = :userId AND YEAR(e.entryDate) = :year AND MONTH(e.entryDate) = :month")
long countDistinctEntryDatesInMonthByUserId(@Param("userId") Long userId, @Param("year") int year, @Param("month") int month);

void deleteAllByUserId(Long userId);
```

- [ ] **Step 5: Update JournalGoalRepository**

```java
List<JournalGoalEntity> findAllByUserId(Long userId);
void deleteAllByUserId(Long userId);
```

- [ ] **Step 6: Update FinanceTransactionRepository**

Add user-scoped queries:

```java
List<FinanceTransactionEntity> findAllByUserIdOrderByPostedAtDesc(Long userId, Pageable pageable);

List<FinanceTransactionEntity> findByUserIdAndCategoryIgnoreCaseOrderByPostedAtDesc(
    Long userId, String category, Pageable pageable);

@Query("SELECT t FROM FinanceTransactionEntity t WHERE t.userId = :userId AND t.postedAt >= :startInclusive AND t.postedAt < :endExclusive ORDER BY t.postedAt DESC")
List<FinanceTransactionEntity> findWithinRangeByUserId(
    @Param("userId") Long userId,
    @Param("startInclusive") Instant startInclusive,
    @Param("endExclusive") Instant endExclusive);

void deleteAllByUserId(Long userId);
```

- [ ] **Step 7: Verify compilation**

Run: `cd $ROOT && ./gradlew compileJava 2>&1 | tail -10`
Expected: Compilation errors in services (they use old repository methods and don't pass userId). This is expected — services are updated in the next tasks.

- [ ] **Step 8: Commit**

```bash
git add $SRC/persistence/
git commit -m "feat: add user_id to all entities and user-scoped repository queries"
```

---

## Task 8: Test Infrastructure

**Files:**
- Create: `$TEST/testsupport/TestUserSeeder.java`
- Modify: `$TEST/testsupport/DatabaseIntegrationTest.java`

- [ ] **Step 1: Create TestUserSeeder**

This component runs in test profile after Flyway migrations and creates a test user:

```java
package com.austinharlan.trading_dashboard.testsupport;

import com.austinharlan.trading_dashboard.persistence.UserEntity;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestUserSeeder implements ApplicationRunner {

  private final UserRepository userRepository;

  public TestUserSeeder(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (userRepository.findByApiKey("test-api-key").isEmpty()) {
      userRepository.save(new UserEntity("test-api-key", "Test User", true, false));
    }
  }
}
```

- [ ] **Step 2: Add test user ID constant to DatabaseIntegrationTest**

Read the current `DatabaseIntegrationTest.java` and add a helper method. The test user is created by `TestUserSeeder` with api_key `"test-api-key"`. Add:

```java
/** API key used by TestUserSeeder — send in X-API-KEY header. */
protected static final String TEST_API_KEY = "test-api-key";
```

- [ ] **Step 3: Verify compilation**

Run: `cd $ROOT && ./gradlew compileTestJava 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add $TEST/testsupport/
git commit -m "feat: add TestUserSeeder and test constants for multi-tenancy"
```

---

## Task 9: Update DefaultPortfolioService

**Files:**
- Modify: `$SRC/service/DefaultPortfolioService.java`

Read the current file first. Then update every method to use `UserContext.current().userId()`:

- [ ] **Step 1: Update service methods**

Key changes:
- `listHoldings()`: use `repository.findAllByUserId(UserContext.current().userId())` and sort
- `summarize()`: scope to user
- `addHolding()`: use `findByUserIdAndTicker()` for upsert, set `userId` on new entities
- `deleteHolding()`: use `deleteByUserIdAndTicker()`

Pattern for each method:
```java
long userId = UserContext.current().userId();
// ... use userId in all repository calls
```

For `addHolding`, the upsert changes from `findByTicker` to `findByUserIdAndTicker`:
```java
public PortfolioHolding addHolding(String ticker, BigDecimal quantity, BigDecimal pricePerShare) {
  long userId = UserContext.current().userId();
  PortfolioPositionEntity position =
      repository.findByUserIdAndTicker(userId, ticker).orElseGet(
          () -> new PortfolioPositionEntity(userId, ticker, BigDecimal.ZERO, BigDecimal.ZERO));
  // ... rest of upsert logic unchanged
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd $ROOT && ./gradlew compileJava 2>&1 | tail -10`

- [ ] **Step 3: Commit**

```bash
git add $SRC/service/DefaultPortfolioService.java
git commit -m "feat: scope DefaultPortfolioService to current user"
```

---

## Task 10: Update DefaultTradeService

**Files:**
- Modify: `$SRC/service/DefaultTradeService.java`

- [ ] **Step 1: Update service methods**

Read the current file first. Key changes:
- `logTrade()`: set `userId` on new `TradeEntity`
- `listTrades()`: use `findFilteredByUserId(userId, ...)`
- `getTrade()`: after fetching, verify `entity.getUserId() == userId`
- `deleteTrade()`: after fetching, verify ownership before delete
- `getClosedTrades()`: use `findAllChronologicalByUserId(userId)`
- `getStats()`: scope to user
- `getPnlHistory()`: scope to user
- `getTradeCalendar()`: scope to user

For `logTrade`, add userId to the entity constructor:
```java
long userId = UserContext.current().userId();
var entity = new TradeEntity(userId, ticker, side, quantity, pricePerShare, date, notes);
```

For `getTrade` and `deleteTrade`, add ownership check:
```java
long userId = UserContext.current().userId();
TradeEntity entity = repository.findById(id).orElseThrow(() -> notFound(id));
if (!entity.getUserId().equals(userId)) {
  throw new EntityNotFoundException("Trade not found: " + id);
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd $ROOT && ./gradlew compileJava 2>&1 | tail -10`

- [ ] **Step 3: Commit**

```bash
git add $SRC/service/DefaultTradeService.java
git commit -m "feat: scope DefaultTradeService to current user"
```

---

## Task 11: Update DefaultJournalService

**Files:**
- Modify: `$SRC/service/DefaultJournalService.java`

- [ ] **Step 1: Update service methods**

Read the current file. Key changes:
- All `entryRepository` calls use user-scoped variants
- `createEntry()`: set userId on new entity
- `updateEntry()`: verify ownership
- `deleteEntry()`: verify ownership
- Goal CRUD: scope to user
- `getStats()`: use user-scoped count/query methods

Pattern: `long userId = UserContext.current().userId();` at start of each method.

- [ ] **Step 2: Verify compilation**

Run: `cd $ROOT && ./gradlew compileJava 2>&1 | tail -10`

- [ ] **Step 3: Commit**

```bash
git add $SRC/service/DefaultJournalService.java
git commit -m "feat: scope DefaultJournalService to current user"
```

---

## Task 12: Update DefaultFinanceInsightsService

**Files:**
- Modify: `$SRC/service/DefaultFinanceInsightsService.java`

- [ ] **Step 1: Update service methods**

Read the current file first. Key changes:
- `getSummary()`: use `findWithinRangeByUserId(userId, ...)`
- `listTransactions()`: use `findAllByUserIdOrderByPostedAtDesc(userId, ...)` and category variant
- Any other methods: scope to user

- [ ] **Step 2: Verify compilation and run full build**

Run: `cd $ROOT && ./gradlew compileJava 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL` — all service compilation errors resolved

- [ ] **Step 3: Commit**

```bash
git add $SRC/service/DefaultFinanceInsightsService.java
git commit -m "feat: scope DefaultFinanceInsightsService to current user"
```

---

## Task 13: Update All Existing Tests

**Files:**
- Modify: `$TEST/TradeIT.java`
- Modify: `$TEST/service/DefaultTradeServiceTest.java`
- Modify: `$TEST/config/ApiKeyAuthFilterTest.java`
- Modify: All other `*IT.java` and `*Test.java` files that construct entities or call old repository methods

> **This task has the widest blast radius.** Run `cd $ROOT && ./gradlew compileTestJava 2>&1 | grep "error:"` first to get the full list of compilation failures. Fix them all before running tests.

- [ ] **Step 1: Identify all broken test files**

Run: `cd $ROOT && ./gradlew compileTestJava 2>&1 | grep "error:" | sort -u`

This will surface every test that uses old entity constructors (missing `userId` param) or old repository methods (e.g., `findByTicker`). Common affected files:
- `TradeIT.java` — `new TradeEntity(...)` missing userId
- `DefaultTradeServiceTest.java` — same
- `ApiKeyAuthFilterTest.java` — constructor changed to `(ApiSecurityProperties, UserRepository)`
- `PortfolioPositionRepositoryIT.java` — `new PortfolioPositionEntity(...)` missing userId, `findByTicker` removed
- `FinanceTransactionRepositoryIT.java` (if exists) — constructor change
- `JournalRepositoryIT.java` (if exists) — constructor change
- `DefaultFinanceInsightsServiceIT.java` (if exists) — constructor/repo changes
- `DefaultJournalServiceTest.java` (if exists) — constructor/repo changes
- `FinanceControllerTest.java` (if exists) — constructor change
- `PortfolioControllerTest.java` (if exists) — constructor change

- [ ] **Step 2: Update TradeIT**

Read the current file. Key changes:
- Inject `UserRepository` to look up the test user's ID
- When creating `TradeEntity` objects directly via repository, pass the test user's `userId`
- The `X-API-KEY: test-api-key` header is already correct (TestUserSeeder creates this user)

In setup (`@BeforeEach` or test methods), get the test user:
```java
@Autowired UserRepository userRepository;

// In test setup:
Long testUserId = userRepository.findByApiKey("test-api-key").orElseThrow().getId();
```

Update all `new TradeEntity(...)` calls to include `testUserId` as first parameter.

- [ ] **Step 3: Update DefaultTradeServiceTest**

The unit test mocks the repository. Update the mock setup to create `TradeEntity` objects with a userId. The `trade()` helper method needs a userId parameter:

```java
private TradeEntity trade(long userId, String ticker, String side, double qty, double price, String date) {
  return new TradeEntity(userId, ticker, side,
      BigDecimal.valueOf(qty), BigDecimal.valueOf(price),
      LocalDate.parse(date), null);
}
```

The FIFO matching tests (`computeClosedTrades`) are static and don't use `UserContext`, so they mostly just need updated constructors.

For tests that call service methods (which internally call `UserContext.current()`), you need to set up a SecurityContext:
```java
private void setUserContext(long userId) {
  var ctx = new UserContext(userId, "Test", false, true);
  var auth = new PreAuthenticatedAuthenticationToken(ctx, "", Collections.emptyList());
  SecurityContextHolder.getContext().setAuthentication(auth);
}

@AfterEach
void clearContext() {
  SecurityContextHolder.clearContext();
}
```

- [ ] **Step 4: Update ApiKeyAuthFilterTest**

The existing test constructs `ApiKeyAuthFilter(properties)`. After Task 5, the constructor is `ApiKeyAuthFilter(ApiSecurityProperties, UserRepository)`. Rewrite the test:
- Mock `UserRepository` with Mockito
- The old `isKeyValid()` constant-time comparison tests no longer apply (the filter does a DB lookup now)
- New test cases: (1) valid key → filter sets UserContext and chains, (2) invalid key → 401, (3) missing key → 401
- Stub `userRepository.findByApiKey("valid-key")` to return a `UserEntity`, verify the `SecurityContextHolder` gets a `UserContext` principal

- [ ] **Step 5: Update all other broken test files**

For each file identified in Step 1:
- Update entity constructor calls to include `userId` as first parameter
- Update repository method calls to user-scoped variants
- For integration tests: use `testUserId` from `UserRepository.findByApiKey("test-api-key")`
- For unit tests with mocks: use a fixed `userId` constant (e.g., `1L`)

- [ ] **Step 6: Verify all tests compile and pass**

Run: `cd $ROOT && ./gradlew test 2>&1 | tail -20`
Expected: All tests pass. Fix any failures before proceeding.

- [ ] **Step 7: Commit**

```bash
git add $TEST/
git commit -m "test: update all existing tests for multi-tenancy user_id"
```

---

## Task 14: DemoService + DemoServiceIT

**Files:**
- Create: `$SRC/service/DemoService.java`
- Create: `$TEST/service/DemoServiceIT.java`

- [ ] **Step 1: Write the failing test**

```java
package com.austinharlan.trading_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.persistence.*;
import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DemoServiceIT extends DatabaseIntegrationTest {

  @Autowired DemoService demoService;
  @Autowired UserRepository userRepository;
  @Autowired PortfolioPositionRepository portfolioRepository;
  @Autowired TradeRepository tradeRepository;
  @Autowired FinanceTransactionRepository financeRepository;
  @Autowired JournalEntryRepository journalEntryRepository;
  @Autowired JournalGoalRepository journalGoalRepository;

  @Test
  void resetDemoData_seedsAllTables() {
    demoService.resetDemoData();

    Long demoUserId = userRepository.findByIsDemoTrue().orElseThrow().getId();
    assertThat(portfolioRepository.findAllByUserId(demoUserId)).hasSize(10);
    assertThat(tradeRepository.findAllByUserIdOrderByTradeDateDescCreatedAtDesc(demoUserId))
        .hasSizeGreaterThanOrEqualTo(15);
    assertThat(financeRepository.findAllByUserIdOrderByPostedAtDesc(demoUserId, org.springframework.data.domain.Pageable.unpaged()))
        .hasSizeGreaterThanOrEqualTo(20);
    assertThat(journalEntryRepository.findAllByUserIdOrderByEntryDateDesc(demoUserId)).hasSize(4);
    assertThat(journalGoalRepository.findAllByUserId(demoUserId)).isEmpty(); // no goals seeded
  }

  @Test
  void resetDemoData_isIdempotent() {
    demoService.resetDemoData();
    demoService.resetDemoData();

    Long demoUserId = userRepository.findByIsDemoTrue().orElseThrow().getId();
    assertThat(portfolioRepository.findAllByUserId(demoUserId)).hasSize(10);
  }

  @Test
  void resetDemoData_doesNotAffectOtherUsers() {
    // Test user created by TestUserSeeder
    Long testUserId = userRepository.findByApiKey("test-api-key").orElseThrow().getId();
    portfolioRepository.save(new PortfolioPositionEntity(
        testUserId, "ZZTEST", java.math.BigDecimal.ONE, java.math.BigDecimal.TEN));
    tradeRepository.save(new TradeEntity(
        testUserId, "ZZTEST", "BUY", java.math.BigDecimal.ONE, java.math.BigDecimal.TEN,
        java.time.LocalDate.now(), "test trade"));

    demoService.resetDemoData();

    assertThat(portfolioRepository.findByUserIdAndTicker(testUserId, "ZZTEST")).isPresent();
    assertThat(tradeRepository.findAllByUserIdOrderByTradeDateDescCreatedAtDesc(testUserId)).hasSize(1);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd $ROOT && ./gradlew test --tests "*.DemoServiceIT" 2>&1 | tail -10`
Expected: FAIL — `DemoService` does not exist

- [ ] **Step 3: Implement DemoService**

Create `$SRC/service/DemoService.java`. This class:
1. Looks up the demo user from `UserRepository`
2. Deletes all existing demo data (respecting FK order — journal entries cascade to tickers/tags)
3. Inserts seed data matching the spec tables

```java
package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoService {

  private final UserRepository userRepository;
  private final PortfolioPositionRepository portfolioRepository;
  private final TradeRepository tradeRepository;
  private final FinanceTransactionRepository financeRepository;
  private final JournalEntryRepository journalEntryRepository;
  private final JournalGoalRepository journalGoalRepository;

  public DemoService(
      UserRepository userRepository,
      PortfolioPositionRepository portfolioRepository,
      TradeRepository tradeRepository,
      FinanceTransactionRepository financeRepository,
      JournalEntryRepository journalEntryRepository,
      JournalGoalRepository journalGoalRepository) {
    this.userRepository = userRepository;
    this.portfolioRepository = portfolioRepository;
    this.tradeRepository = tradeRepository;
    this.financeRepository = financeRepository;
    this.journalEntryRepository = journalEntryRepository;
    this.journalGoalRepository = journalGoalRepository;
  }

  @Transactional
  public void resetDemoData() {
    Long demoUserId = userRepository.findByIsDemoTrue()
        .orElseThrow(() -> new IllegalStateException("Demo user not found"))
        .getId();

    // Delete in FK-safe order (journal_entry_tickers/tags cascade from journal_entries)
    journalEntryRepository.deleteAllByUserId(demoUserId);
    journalGoalRepository.deleteAllByUserId(demoUserId);
    tradeRepository.deleteAllByUserId(demoUserId);
    financeRepository.deleteAllByUserId(demoUserId);
    portfolioRepository.deleteAllByUserId(demoUserId);

    seedPortfolio(demoUserId);
    seedTrades(demoUserId);
    seedFinanceTransactions(demoUserId);
    seedJournalEntries(demoUserId);
  }

  // Seed methods insert data per the spec tables.
  // See spec: docs/superpowers/specs/2026-03-25-multi-tenancy-demo-mode-design.md
  // "Demo Seed Data" section for exact values.

  private void seedPortfolio(Long userId) {
    // 10 positions from spec
    portfolio(userId, "NVDA", "25", "118.50");
    portfolio(userId, "OKLO", "100", "23.75");
    portfolio(userId, "MRVL", "50", "72.30");
    portfolio(userId, "AAPL", "30", "185.20");
    portfolio(userId, "MSFT", "15", "415.60");
    portfolio(userId, "AMZN", "20", "186.40");
    portfolio(userId, "LLY", "8", "790.00");
    portfolio(userId, "JPM", "25", "198.50");
    portfolio(userId, "COST", "10", "875.30");
    portfolio(userId, "PLTR", "75", "42.15");
  }

  private void portfolio(Long userId, String ticker, String qty, String basis) {
    portfolioRepository.save(
        new PortfolioPositionEntity(userId, ticker, new BigDecimal(qty), new BigDecimal(basis)));
  }

  private void seedTrades(Long userId) {
    // 16 trades from spec
    trade(userId, "2026-01-06", "NVDA", "BUY", "25", "118.50", "Initial position");
    trade(userId, "2026-01-06", "AAPL", "BUY", "30", "185.20", "Core holding");
    trade(userId, "2026-01-08", "MRVL", "BUY", "50", "72.30", "Semiconductor play");
    trade(userId, "2026-01-10", "MSFT", "BUY", "15", "415.60", "Cloud/AI thesis");
    trade(userId, "2026-01-13", "PLTR", "BUY", "75", "42.15", "Defense/AI");
    trade(userId, "2026-01-15", "OKLO", "BUY", "100", "23.75", "Nuclear energy bet");
    trade(userId, "2026-01-22", "AMZN", "BUY", "20", "186.40", "AWS growth");
    trade(userId, "2026-01-27", "JPM", "BUY", "25", "198.50", "Financials exposure");
    trade(userId, "2026-02-03", "LLY", "BUY", "8", "790.00", "Pharma/GLP-1");
    trade(userId, "2026-02-10", "COST", "BUY", "10", "875.30", "Consumer staples");
    trade(userId, "2026-02-14", "SOFI", "BUY", "200", "11.25", "Swing trade");
    trade(userId, "2026-02-28", "SOFI", "SELL", "200", "13.80", "+$510 winner");
    trade(userId, "2026-03-03", "TSLA", "BUY", "15", "245.00", "Bounce play");
    trade(userId, "2026-03-10", "TSLA", "SELL", "15", "232.50", "-$187.50 cut loss");
    trade(userId, "2026-03-17", "AMD", "BUY", "40", "108.75", "Dip buy");
    trade(userId, "2026-03-21", "AMD", "SELL", "40", "115.20", "+$258 quick flip");
  }

  private void trade(Long userId, String date, String ticker, String side,
      String qty, String price, String notes) {
    tradeRepository.save(new TradeEntity(
        userId, ticker, side, new BigDecimal(qty), new BigDecimal(price),
        LocalDate.parse(date), notes));
  }

  private void seedFinanceTransactions(Long userId) {
    // 25 transactions from spec — use helper that generates UUID id
    txn(userId, "2026-02-01T12:00:00Z", "Spotify Premium", "-10.99", "Subscriptions");
    txn(userId, "2026-02-01T12:00:00Z", "Netflix", "-15.49", "Subscriptions");
    txn(userId, "2026-02-03T12:00:00Z", "Whole Foods", "-87.32", "Groceries");
    txn(userId, "2026-02-05T12:00:00Z", "Shell Gas", "-52.40", "Transportation");
    txn(userId, "2026-02-07T12:00:00Z", "Chipotle", "-14.25", "Restaurants");
    txn(userId, "2026-02-08T12:00:00Z", "Amazon - Headphones", "-79.99", "Shopping");
    txn(userId, "2026-02-10T12:00:00Z", "Kroger", "-63.18", "Groceries");
    txn(userId, "2026-02-12T12:00:00Z", "Uber Eats", "-28.45", "Restaurants");
    txn(userId, "2026-02-14T12:00:00Z", "Salary Deposit", "4250.00", "Income");
    txn(userId, "2026-02-15T12:00:00Z", "Rent", "-1450.00", "Housing");
    txn(userId, "2026-02-17T12:00:00Z", "Electric Bill", "-89.50", "Utilities");
    txn(userId, "2026-02-19T12:00:00Z", "Trader Joe's", "-45.67", "Groceries");
    txn(userId, "2026-02-21T12:00:00Z", "Gym Membership", "-35.00", "Health");
    txn(userId, "2026-02-23T12:00:00Z", "Target", "-42.88", "Shopping");
    txn(userId, "2026-02-25T12:00:00Z", "Happy Hour - Brewpub", "-36.50", "Restaurants");
    txn(userId, "2026-02-28T12:00:00Z", "Salary Deposit", "4250.00", "Income");
    txn(userId, "2026-03-01T12:00:00Z", "Spotify Premium", "-10.99", "Subscriptions");
    txn(userId, "2026-03-01T12:00:00Z", "Netflix", "-15.49", "Subscriptions");
    txn(userId, "2026-03-03T12:00:00Z", "Costco", "-156.23", "Groceries");
    txn(userId, "2026-03-05T12:00:00Z", "Shell Gas", "-48.90", "Transportation");
    txn(userId, "2026-03-07T12:00:00Z", "Panda Express", "-12.75", "Restaurants");
    txn(userId, "2026-03-10T12:00:00Z", "Whole Foods", "-71.44", "Groceries");
    txn(userId, "2026-03-12T12:00:00Z", "AWS Bill", "-23.47", "Subscriptions");
    txn(userId, "2026-03-15T12:00:00Z", "Rent", "-1450.00", "Housing");
    txn(userId, "2026-03-18T12:00:00Z", "Dentist Copay", "-40.00", "Health");
  }

  private void txn(Long userId, String postedAt, String description, String amount, String category) {
    financeRepository.save(new FinanceTransactionEntity(
        userId, Instant.parse(postedAt), description, new BigDecimal(amount), category, null));
  }

  private void seedJournalEntries(Long userId) {
    journalEntry(userId, "2026-01-06",
        "Opened initial positions today. Heavy on semiconductors (NVDA, MRVL) and added nuclear exposure with OKLO. Thesis: AI capex cycle drives chip demand, nuclear provides baseload for data centers. Setting a 6-month review point.",
        new String[]{"NVDA", "MRVL", "OKLO"}, new String[]{"thesis", "new-position"});
    journalEntry(userId, "2026-02-14",
        "Entered SOFI at $11.25 for a swing. Fintech has been beaten down but earnings were solid. Target $13-14 range, stop at $10. Small position, willing to lose it.",
        new String[]{"SOFI"}, new String[]{"swing-trade", "entry"});
    journalEntry(userId, "2026-02-28",
        "Sold SOFI at $13.80 for +$510. Hit the target zone. Keeping the win rate positive matters more than size. Looking at TSLA for a bounce play next week.",
        new String[]{"SOFI", "TSLA"}, new String[]{"exit", "win"});
    journalEntry(userId, "2026-03-10",
        "Cut TSLA at $232.50 for a -$187.50 loss. Bounce thesis didn't play out — macro headwinds too strong. Lesson: don't fight the trend. Moved into AMD on the semiconductor dip instead.",
        new String[]{"TSLA", "AMD"}, new String[]{"exit", "loss", "lesson"});
  }

  private void journalEntry(Long userId, String date, String body,
      String[] tickers, String[] tags) {
    var entry = new JournalEntryEntity(userId, body, LocalDate.parse(date),
        new java.util.LinkedHashSet<>(java.util.Arrays.asList(tickers)),
        new java.util.LinkedHashSet<>(java.util.Arrays.asList(tags)));
    journalEntryRepository.save(entry);
  }
}
```

**Important implementation notes:**
- `FinanceTransactionEntity(Long userId, Instant, String, BigDecimal, String, String)` — auto-UUID constructor from Task 7
- `JournalEntryEntity(Long userId, String body, LocalDate, Set<String> tickers, Set<String> tags)` — pass tickers/tags at construction. Do NOT call `getTickers().add()` — it returns an unmodifiable set and will throw `UnsupportedOperationException`

- [ ] **Step 4: Run test to verify it passes**

Run: `cd $ROOT && ./gradlew test --tests "*.DemoServiceIT" 2>&1 | tail -15`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add $SRC/service/DemoService.java $TEST/service/DemoServiceIT.java
git commit -m "feat: add DemoService with seed data for all tables"
```

---

## Task 15: DemoController + DemoIT

**Files:**
- Create: `$SRC/controllers/DemoController.java`
- Create: `$TEST/DemoIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
package com.austinharlan.trading_dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.persistence.PortfolioPositionRepository;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoIT extends DatabaseIntegrationTest {

  @Autowired TestRestTemplate rest;
  @Autowired UserRepository userRepository;
  @Autowired PortfolioPositionRepository portfolioRepository;

  @Test
  @SuppressWarnings("unchecked")
  void postDemoSession_returns200WithApiKey() {
    ResponseEntity<Map> response = rest.postForEntity("/api/demo/session", null, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("apiKey", "demo");
  }

  @Test
  void postDemoSession_seedsDemoData() {
    rest.postForEntity("/api/demo/session", null, Map.class);

    Long demoUserId = userRepository.findByIsDemoTrue().orElseThrow().getId();
    assertThat(portfolioRepository.findAllByUserId(demoUserId)).hasSize(10);
  }

  @Test
  void postDemoSession_isIdempotent() {
    rest.postForEntity("/api/demo/session", null, Map.class);
    rest.postForEntity("/api/demo/session", null, Map.class);

    Long demoUserId = userRepository.findByIsDemoTrue().orElseThrow().getId();
    assertThat(portfolioRepository.findAllByUserId(demoUserId)).hasSize(10);
  }

  @Test
  void postDemoSession_requiresNoAuth() {
    // No X-API-KEY header — should still succeed
    ResponseEntity<Map> response = rest.postForEntity("/api/demo/session", null, Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd $ROOT && ./gradlew test --tests "*.DemoIT" 2>&1 | tail -10`
Expected: FAIL — 404 (endpoint doesn't exist)

- [ ] **Step 3: Create DemoController**

```java
package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.service.DemoService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

  private final DemoService demoService;

  public DemoController(DemoService demoService) {
    this.demoService = demoService;
  }

  @PostMapping("/api/demo/session")
  public ResponseEntity<Map<String, String>> startDemoSession() {
    demoService.resetDemoData();
    return ResponseEntity.ok(Map.of("apiKey", "demo"));
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd $ROOT && ./gradlew test --tests "*.DemoIT" 2>&1 | tail -15`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add $SRC/controllers/DemoController.java $TEST/DemoIT.java
git commit -m "feat: add DemoController with POST /api/demo/session endpoint"
```

---

## Task 16: AdminController + AdminIT

**Files:**
- Create: `$SRC/controllers/AdminController.java`
- Create: `$TEST/AdminIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
package com.austinharlan.trading_dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.persistence.UserRepository;
import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminIT extends DatabaseIntegrationTest {

  @Autowired TestRestTemplate rest;
  @Autowired UserRepository userRepository;

  private HttpHeaders adminHeaders() {
    HttpHeaders h = new HttpHeaders();
    h.set("X-API-KEY", "test-api-key"); // admin user from TestUserSeeder
    h.setContentType(MediaType.APPLICATION_JSON);
    return h;
  }

  private HttpHeaders demoHeaders() {
    HttpHeaders h = new HttpHeaders();
    h.set("X-API-KEY", "demo");
    h.setContentType(MediaType.APPLICATION_JSON);
    return h;
  }

  @Test
  @SuppressWarnings("unchecked")
  void createUser_withAdminKey_returns201() {
    var body = Map.of("displayName", "Jake");
    var request = new HttpEntity<>(body, adminHeaders());

    ResponseEntity<Map> response = rest.exchange("/api/admin/users", HttpMethod.POST, request, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsKeys("id", "displayName", "apiKey");
    assertThat(response.getBody().get("displayName")).isEqualTo("Jake");
    assertThat((String) response.getBody().get("apiKey")).isNotBlank();
  }

  @Test
  void createUser_withDemoKey_returns403() {
    var body = Map.of("displayName", "Hacker");
    var request = new HttpEntity<>(body, demoHeaders());

    ResponseEntity<Map> response = rest.exchange("/api/admin/users", HttpMethod.POST, request, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void createUser_withNoKey_returns401() {
    var body = Map.of("displayName", "Nobody");
    var request = new HttpEntity<>(body);

    ResponseEntity<Map> response = rest.exchange("/api/admin/users", HttpMethod.POST, request, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd $ROOT && ./gradlew test --tests "*.AdminIT" 2>&1 | tail -10`
Expected: FAIL — 404

- [ ] **Step 3: Create AdminController**

```java
package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.config.UserContext;
import com.austinharlan.trading_dashboard.persistence.UserEntity;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

  private final UserRepository userRepository;

  public AdminController(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @PostMapping("/api/admin/users")
  public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, String> body) {
    UserContext ctx = UserContext.current();
    if (!ctx.isAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    String displayName = body.get("displayName");
    if (displayName == null || displayName.isBlank()) {
      return ResponseEntity.badRequest().build();
    }

    String apiKey = UUID.randomUUID().toString();
    UserEntity user = userRepository.save(new UserEntity(apiKey, displayName, false, false));

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of(
            "id", user.getId(),
            "displayName", user.getDisplayName(),
            "apiKey", user.getApiKey()));
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd $ROOT && ./gradlew test --tests "*.AdminIT" 2>&1 | tail -15`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add $SRC/controllers/AdminController.java $TEST/AdminIT.java
git commit -m "feat: add AdminController with POST /api/admin/users (admin-gated)"
```

---

## Task 17: MultiTenancyIT

**Files:**
- Create: `$TEST/MultiTenancyIT.java`

This is the critical test that verifies no data leaks between users.

- [ ] **Step 1: Write the test**

```java
package com.austinharlan.trading_dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.persistence.*;
import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultiTenancyIT extends DatabaseIntegrationTest {

  @Autowired TestRestTemplate rest;
  @Autowired UserRepository userRepository;
  @Autowired PortfolioPositionRepository portfolioRepository;

  private String userAKey;
  private String userBKey;

  @BeforeEach
  void setup() {
    // Create two users via admin endpoint
    HttpHeaders admin = new HttpHeaders();
    admin.set("X-API-KEY", "test-api-key");
    admin.setContentType(MediaType.APPLICATION_JSON);

    var respA = rest.exchange("/api/admin/users", HttpMethod.POST,
        new HttpEntity<>(Map.of("displayName", "Alice"), admin), Map.class);
    userAKey = (String) respA.getBody().get("apiKey");

    var respB = rest.exchange("/api/admin/users", HttpMethod.POST,
        new HttpEntity<>(Map.of("displayName", "Bob"), admin), Map.class);
    userBKey = (String) respB.getBody().get("apiKey");
  }

  @org.junit.jupiter.api.AfterEach
  void cleanup() {
    // Clean up test users' positions to avoid state leakage
    if (userAKey != null) {
      userRepository.findByApiKey(userAKey).ifPresent(u -> portfolioRepository.deleteAllByUserId(u.getId()));
    }
    if (userBKey != null) {
      userRepository.findByApiKey(userBKey).ifPresent(u -> portfolioRepository.deleteAllByUserId(u.getId()));
    }
  }

  @Test
  void usersOnlySeeTheirOwnPositions() {
    // Alice adds a position
    HttpHeaders hA = headers(userAKey);
    rest.exchange("/api/portfolio/positions", HttpMethod.POST,
        new HttpEntity<>(Map.of("ticker", "AAPL", "quantity", 10, "pricePerShare", 150.0), hA),
        Map.class);

    // Bob adds a position
    HttpHeaders hB = headers(userBKey);
    rest.exchange("/api/portfolio/positions", HttpMethod.POST,
        new HttpEntity<>(Map.of("ticker", "GOOG", "quantity", 5, "pricePerShare", 170.0), hB),
        Map.class);

    // Alice should only see AAPL
    ResponseEntity<List> alicePositions = rest.exchange(
        "/api/portfolio/positions", HttpMethod.GET, new HttpEntity<>(hA), List.class);
    assertThat(alicePositions.getBody()).hasSize(1);

    // Bob should only see GOOG
    ResponseEntity<List> bobPositions = rest.exchange(
        "/api/portfolio/positions", HttpMethod.GET, new HttpEntity<>(hB), List.class);
    assertThat(bobPositions.getBody()).hasSize(1);
  }

  private HttpHeaders headers(String key) {
    HttpHeaders h = new HttpHeaders();
    h.set("X-API-KEY", key);
    h.setContentType(MediaType.APPLICATION_JSON);
    return h;
  }
}
```

- [ ] **Step 2: Run test**

Run: `cd $ROOT && ./gradlew test --tests "*.MultiTenancyIT" 2>&1 | tail -15`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add $TEST/MultiTenancyIT.java
git commit -m "test: add MultiTenancyIT verifying data isolation between users"
```

---

## Task 18: Update OpenAPI Spec

**Files:**
- Modify: `$ROOT/openAPI.yaml`

- [ ] **Step 1: Add new endpoints and schemas**

Add to the `paths` section:

```yaml
  /api/demo/session:
    post:
      operationId: startDemoSession
      summary: Start a demo session
      description: Resets and seeds demo data, returns the demo API key. No auth required.
      security: []
      tags:
        - Demo
      responses:
        '200':
          description: Demo session started
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/DemoSessionResponse'

  /api/admin/users:
    post:
      operationId: createUser
      summary: Create a new user (admin only)
      tags:
        - Admin
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateUserRequest'
      responses:
        '201':
          description: User created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CreateUserResponse'
        '403':
          description: Requesting user is not admin
```

Add to `components/schemas`:

```yaml
    DemoSessionResponse:
      type: object
      required: [apiKey]
      properties:
        apiKey:
          type: string

    CreateUserRequest:
      type: object
      required: [displayName]
      properties:
        displayName:
          type: string

    CreateUserResponse:
      type: object
      required: [id, displayName, apiKey]
      properties:
        id:
          type: integer
          format: int64
        displayName:
          type: string
        apiKey:
          type: string
```

**Convention exception:** `DemoController` and `AdminController` do not implement generated interfaces — they are too simple (one method each) to warrant code generation. This is an accepted deviation from the project's OpenAPI-first controller pattern documented in CLAUDE.md. The OpenAPI additions serve documentation and client generation purposes.

- [ ] **Step 2: Regenerate**

Run: `cd $ROOT && ./gradlew openApiGenerate 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add $ROOT/openAPI.yaml
git commit -m "docs: add demo session and admin user endpoints to OpenAPI spec"
```

---

## Task 19: Frontend Changes

**Files:**
- Modify: `$RES/static/index.html`

This is the largest single-file change. Three areas: (1) login screen redesign, (2) demo session flow, (3) demo mode banner.

- [ ] **Step 1: Replace the login overlay HTML**

Find the existing overlay `<div class="overlay" id="overlay">` and replace its contents with the demo-first layout from the spec. Key structure:

```html
<div class="overlay" id="overlay">
  <div class="lock-box">
    <div class="lock-title">Benji</div>
    <div class="lock-sub">Your personal financial assistant</div>

    <!-- Demo hero -->
    <div class="demo-hero" id="demoBtn" onclick="startDemo()">
      <div class="demo-glow"></div>
      <div class="demo-title">&#10022; Try Demo</div>
      <div class="demo-sub">Explore a pre-built portfolio with live market data.<br>No login required.</div>
    </div>

    <!-- Divider -->
    <div class="lock-divider"></div>

    <!-- Returning user section -->
    <div class="lock-returning">
      <div class="lock-returning-label">Already have an API key?</div>
      <div class="lock-returning-row">
        <div class="lock-key-wrap">
          <input class="field lock-key-input" id="keyInput" type="password" placeholder="Enter key" autocomplete="off">
        </div>
        <button class="btn lock-go-btn" id="unlockBtn">Go &rarr;</button>
      </div>
    </div>
    <div class="lock-err" id="unlockErr"></div>
  </div>
</div>
```

- [ ] **Step 2: Add CSS for new login screen + demo banner**

Add to the `<style>` section:

```css
/* Demo hero card */
.demo-hero{background:linear-gradient(135deg,#4edd8a15,#4edd8a08);border:1px solid #4edd8a44;border-radius:10px;padding:20px;margin-bottom:18px;cursor:pointer;position:relative;overflow:hidden;transition:border-color .2s}
.demo-hero:hover{border-color:#4edd8a88}
.demo-glow{position:absolute;top:-20px;right:-20px;width:80px;height:80px;background:radial-gradient(circle,#4edd8a15,transparent);border-radius:50%}
.demo-title{font-size:15px;color:#4edd8a;font-weight:600;margin-bottom:6px;position:relative}
.demo-sub{font-size:10px;color:#4edd8a99;line-height:1.5;position:relative}

/* Returning user section */
.lock-divider{border-top:1px solid #1a1a2e;margin:14px 0;padding-top:14px}
.lock-returning-label{font-size:10px;color:#e08a4a;margin-bottom:8px;letter-spacing:.3px}
.lock-returning-row{display:flex;gap:6px}
.lock-key-wrap{flex:1;position:relative}
.lock-key-input{width:100%;background:#111!important;border:1px solid #e08a4a33!important;border-radius:6px!important;padding:9px 10px!important;font-size:11px!important;color:#e0e0e0!important;animation:pulse-border 2.5s ease-in-out infinite}
.lock-go-btn{background:#e08a4a22!important;border:1px solid #e08a4a55!important;border-radius:6px!important;padding:9px 14px!important;color:#e08a4a!important;font-size:11px!important;font-weight:600!important;cursor:pointer}
.lock-go-btn:hover{background:#e08a4a33!important}

@keyframes pulse-border{0%,100%{border-color:#e08a4a33}50%{border-color:#e08a4a88}}

/* Demo mode banner */
.demo-banner{position:fixed;top:0;left:0;right:0;height:32px;background:#0d1a12;border-bottom:1px solid #4edd8a33;display:flex;align-items:center;justify-content:center;gap:12px;font-size:11px;color:#4edd8a99;z-index:10000;overflow:hidden}
.demo-banner::before{content:'';position:absolute;top:0;left:-200px;width:200px;height:100%;background:linear-gradient(90deg,transparent,#4edd8a11,transparent);animation:demo-shimmer 3s ease-in-out infinite}
@keyframes demo-shimmer{0%{left:-200px}100%{left:100%}}
.demo-banner a{color:#4edd8a;text-decoration:none;font-weight:600}
.demo-banner a:hover{text-decoration:underline}
```

- [ ] **Step 3: Add demo banner HTML**

Add immediately inside `<body>`, before the sidebar:

```html
<div class="demo-banner" id="demoBanner" style="display:none">
  Demo Mode &mdash; changes reset each session
  <a href="#" onclick="signOutDemo();return false">Sign in &rarr;</a>
</div>
```

When demo banner is visible, push the app layout down by adding `body.demo-mode` class:
```css
body.demo-mode .sidebar{top:32px;height:calc(100vh - 32px)}
body.demo-mode .main{margin-top:32px}
```

- [ ] **Step 4: Update JavaScript — demo session flow**

Add `startDemo()` function:
```javascript
async function startDemo() {
  const btn = document.getElementById('demoBtn');
  const title = btn.querySelector('.demo-title');
  const origText = title.textContent;
  title.textContent = 'Starting demo...';
  btn.style.pointerEvents = 'none';
  try {
    const r = await fetch('/api/demo/session', { method: 'POST' });
    if (r.ok) {
      const data = await r.json();
      KEY = data.apiKey;
      sessionStorage.setItem('KEY', KEY);
      localStorage.setItem('benji_watchlist', JSON.stringify(['TSLA','GOOG','META','AMD','SOFI','COIN']));
      launch();
    } else {
      document.getElementById('unlockErr').textContent = 'Failed to start demo';
    }
  } catch (e) {
    document.getElementById('unlockErr').textContent = 'Connection error';
  } finally {
    title.textContent = origText;
    btn.style.pointerEvents = '';
  }
}
```

- [ ] **Step 5: Update `launch()` to show demo banner**

After `launch()` hides the overlay, check if demo mode:
```javascript
function launch() {
  document.getElementById('overlay').style.display = 'none';
  if (KEY === 'demo') {
    document.getElementById('demoBanner').style.display = 'flex';
    document.body.classList.add('demo-mode');
  }
  // ... existing dashboard loading code
}
```

Add `signOutDemo()`:
```javascript
function signOutDemo() {
  KEY = '';
  sessionStorage.removeItem('KEY');
  localStorage.removeItem('benji_watchlist');
  document.getElementById('demoBanner').style.display = 'none';
  document.body.classList.remove('demo-mode');
  document.getElementById('overlay').style.display = 'flex';
}
```

- [ ] **Step 6: Verify locally**

Run: `cd $ROOT && ./gradlew bootRun`
Open `http://localhost:8080` in browser. Verify:
- Login screen shows demo-first layout with glowing ember colors
- "Try Demo" button works and loads demo data
- Demo banner appears at top
- "Sign in" link returns to login screen
- Regular API key login still works

- [ ] **Step 7: Commit**

```bash
git add $RES/static/index.html
git commit -m "feat: redesign login screen with demo-first layout and demo mode banner"
```

---

## Task 20: Spotless + Full Build + Push

- [ ] **Step 1: Run Spotless**

Run: `cd $ROOT && ./gradlew spotlessApply 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Commit formatting changes (if any)**

```bash
git add -A && git diff --cached --quiet || git commit -m "style: apply spotless formatting"
```

- [ ] **Step 3: Full build with tests**

Run: `cd $ROOT && ./gradlew build 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL` with all tests passing

- [ ] **Step 4: Fix any failures**

If tests fail, investigate and fix. Common issues:
- Entity constructor signature mismatches
- Missing `userId` in test data setup
- Repository method name mismatches (Spring Data naming conventions)

- [ ] **Step 5: Push**

```bash
git push origin main
```

Verify CI passes at the GitHub Actions page. The deploy will auto-run on push to main.
