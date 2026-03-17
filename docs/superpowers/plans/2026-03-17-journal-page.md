# Journal Page Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a full-featured investing journal page to the trading dashboard SPA — a write-first two-column layout with neon inline tokens, rich-text editor, media embeds, calendar, goals, and movers sidebar.

**Architecture:** OpenAPI-first backend (four new DB tables via Flyway V3, `JournalController → JournalService → two JPA repositories`), single-file frontend addition to `index.html` (new `page-journal` div, sidebar nav item, vanilla JS for token detection and all sidebar widgets).

**Tech Stack:** Spring Boot 3 / Spring Data JPA / H2 (dev+test) / Postgres (prod) / Flyway / JUnit 5 + AssertJ + Testcontainers / Vanilla JS + CSS inside `index.html`

---

> All Gradle commands run from: `apps/api/trader-assistant/trading-dashboard`
> All file paths are relative to that directory unless fully qualified.

---

## Chunk 1: Backend — Migration, OpenAPI, Entities, Repos, Service, Controller

---

### Task 1: Flyway Migration

**Files:**
- Create: `src/main/resources/db/migration/V3__journal.sql`

- [ ] **Step 1.1: Write the migration**

```sql
-- src/main/resources/db/migration/V3__journal.sql

create table journal_entries (
    id          bigserial primary key,
    body        text not null,
    entry_date  date not null default current_date,
    created_at  timestamp with time zone not null default now(),
    updated_at  timestamp with time zone not null default now(),
    constraint uq_journal_entries_entry_date unique (entry_date)
);

create table journal_entry_tickers (
    entry_id    bigint not null references journal_entries(id) on delete cascade,
    ticker      text not null,
    primary key (entry_id, ticker)
);

create table journal_entry_tags (
    entry_id    bigint not null references journal_entries(id) on delete cascade,
    tag         text not null,
    primary key (entry_id, tag)
);

create table journal_goals (
    id               bigserial primary key,
    label            text not null,
    goal_type        text not null check (goal_type in ('milestone', 'habit')),
    target_value     numeric,
    milestone_value  numeric,
    deadline         date,
    created_at       timestamp with time zone not null default now()
);
```

- [ ] **Step 1.2: Commit**

```bash
git add src/main/resources/db/migration/V3__journal.sql
git commit -m "feat: add V3 Flyway migration for journal tables"
```

---

### Task 2: OpenAPI Spec — Journal Paths and Schemas

**Files:**
- Modify: `openAPI.yaml`

The `Journal` tag generates interface `com.austinharlan.tradingdashboard.api.JournalApi` and DTOs in `com.austinharlan.tradingdashboard.dto`.

- [ ] **Step 2.1: Add journal paths to `openAPI.yaml`**

Add the following paths after the last existing path (before `components:`):

```yaml
  /api/journal/entries:
    get:
      tags:
        - Journal
      operationId: listJournalEntries
      summary: List journal entries, newest first.
      parameters:
        - name: ticker
          in: query
          required: false
          schema:
            type: string
        - name: tag
          in: query
          required: false
          schema:
            type: string
        - name: entryDate
          in: query
          required: false
          schema:
            type: string
            format: date
      responses:
        '200':
          description: List of journal entries.
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/JournalEntryResponse'
    post:
      tags:
        - Journal
      operationId: createJournalEntry
      summary: Create a new journal entry.
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/JournalEntryRequest'
      responses:
        '201':
          description: Entry created.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/JournalEntryResponse'
        '400':
          description: Invalid request body.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
  /api/journal/entries/{id}:
    put:
      tags:
        - Journal
      operationId: updateJournalEntry
      summary: Update an existing journal entry body.
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/JournalEntryRequest'
      responses:
        '200':
          description: Entry updated.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/JournalEntryResponse'
        '404':
          description: Entry not found.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
    delete:
      tags:
        - Journal
      operationId: deleteJournalEntry
      summary: Delete a journal entry.
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '204':
          description: Entry deleted.
        '404':
          description: Entry not found.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
  /api/journal/goals:
    get:
      tags:
        - Journal
      operationId: listJournalGoals
      summary: List all journal goals with computed progress.
      responses:
        '200':
          description: List of goals.
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/JournalGoalResponse'
    post:
      tags:
        - Journal
      operationId: createJournalGoal
      summary: Create a new journal goal.
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/JournalGoalRequest'
      responses:
        '201':
          description: Goal created.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/JournalGoalResponse'
        '400':
          description: Invalid request body.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
  /api/journal/goals/{id}:
    put:
      tags:
        - Journal
      operationId: updateJournalGoal
      summary: Update an existing journal goal.
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/JournalGoalRequest'
      responses:
        '200':
          description: Goal updated.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/JournalGoalResponse'
        '404':
          description: Goal not found.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
    delete:
      tags:
        - Journal
      operationId: deleteJournalGoal
      summary: Delete a journal goal.
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '204':
          description: Goal deleted.
        '404':
          description: Goal not found.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
  /api/journal/stats:
    get:
      tags:
        - Journal
      operationId: getJournalStats
      summary: Return journal statistics including streak, calendar, and most-mentioned tokens.
      responses:
        '200':
          description: Journal statistics.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/JournalStatsResponse'
```

- [ ] **Step 2.2: Add journal schemas to `openAPI.yaml`** (inside `components.schemas:`)

```yaml
    JournalEntryRequest:
      type: object
      required:
        - body
        - entry_date
      properties:
        body:
          type: string
          description: HTML content of the journal entry.
          example: '<p>$NVDA holding strong today. #macro</p>'
        entry_date:
          type: string
          format: date
          description: Server-local date for this entry (YYYY-MM-DD).
          example: '2026-03-17'
    JournalEntryResponse:
      type: object
      required:
        - id
        - body
        - entry_date
        - tickers
        - tags
        - created_at
        - updated_at
      properties:
        id:
          type: integer
          format: int64
        body:
          type: string
        entry_date:
          type: string
          format: date
        tickers:
          type: array
          items:
            type: string
          example: ['NVDA', 'MSFT']
        tags:
          type: array
          items:
            type: string
          example: ['macro', 'growth']
        created_at:
          type: string
          format: date-time
        updated_at:
          type: string
          format: date-time
    JournalGoalRequest:
      type: object
      required:
        - label
        - goal_type
      properties:
        label:
          type: string
          example: 'Hit $500K'
        goal_type:
          type: string
          enum: [milestone, habit]
        target_value:
          type: number
          format: double
          nullable: true
        milestone_value:
          type: number
          format: double
          nullable: true
        deadline:
          type: string
          format: date
          nullable: true
    JournalGoalResponse:
      type: object
      required:
        - id
        - label
        - goal_type
        - created_at
      properties:
        id:
          type: integer
          format: int64
        label:
          type: string
        goal_type:
          type: string
          enum: [milestone, habit]
        target_value:
          type: number
          format: double
          nullable: true
        current_progress:
          type: number
          format: double
          nullable: true
        progress_pct:
          type: number
          format: double
          nullable: true
        deadline:
          type: string
          format: date
          nullable: true
        created_at:
          type: string
          format: date-time
    JournalStatsResponse:
      type: object
      required:
        - entry_count
        - current_streak
        - calendar
        - most_mentioned
      properties:
        entry_count:
          type: integer
          format: int64
        current_streak:
          type: integer
        calendar:
          type: object
          description: Map of ISO date string to activity level (0=none, 1=entry, 2=rich entry).
          additionalProperties:
            type: integer
          example:
            '2026-03-01': 0
            '2026-03-15': 1
            '2026-03-17': 2
        most_mentioned:
          type: array
          items:
            $ref: '#/components/schemas/JournalTokenCount'
    JournalTokenCount:
      type: object
      required:
        - token
        - count
      properties:
        token:
          type: string
          example: '$NVDA'
        count:
          type: integer
```

- [ ] **Step 2.3: Run code generation**

```bash
./gradlew openApiGenerate
```

Expected: BUILD SUCCESSFUL. Generated files appear in `build/generated/openapi/src/main/java/com/austinharlan/tradingdashboard/api/JournalApi.java` and corresponding DTOs in `...dto/`.

- [ ] **Step 2.4: Commit**

```bash
git add openAPI.yaml
git commit -m "feat: add journal endpoints and schemas to OpenAPI spec"
```

---

### Task 3: JPA Entities

**Files:**
- Create: `src/main/java/com/austinharlan/trading_dashboard/persistence/JournalEntryEntity.java`
- Create: `src/main/java/com/austinharlan/trading_dashboard/persistence/JournalGoalEntity.java`

> `@ElementCollection` with `@CollectionTable` is the idiomatic JPA way to map simple string collections to normalized tables — avoids needing separate entity classes for `journal_entry_tickers` and `journal_entry_tags`.

- [ ] **Step 3.1: Create `JournalEntryEntity.java`**

```java
package com.austinharlan.trading_dashboard.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "journal_entries")
public class JournalEntryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "body", nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "entry_date", nullable = false)
  private LocalDate entryDate;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "journal_entry_tickers",
      joinColumns = @JoinColumn(name = "entry_id"))
  @Column(name = "ticker")
  private Set<String> tickers = new HashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "journal_entry_tags",
      joinColumns = @JoinColumn(name = "entry_id"))
  @Column(name = "tag")
  private Set<String> tags = new HashSet<>();

  protected JournalEntryEntity() {}

  public JournalEntryEntity(String body, LocalDate entryDate, Set<String> tickers, Set<String> tags) {
    this.body = Objects.requireNonNull(body, "body must not be null");
    this.entryDate = Objects.requireNonNull(entryDate, "entryDate must not be null");
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
    this.tickers = tickers != null ? new HashSet<>(tickers) : new HashSet<>();
    this.tags = tags != null ? new HashSet<>(tags) : new HashSet<>();
  }

  public Long getId() { return id; }
  public String getBody() { return body; }
  public void setBody(String body) { this.body = Objects.requireNonNull(body); }
  public LocalDate getEntryDate() { return entryDate; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = Objects.requireNonNull(updatedAt); }
  public Set<String> getTickers() { return tickers; }
  public void setTickers(Set<String> tickers) { this.tickers = tickers != null ? new HashSet<>(tickers) : new HashSet<>(); }
  public Set<String> getTags() { return tags; }
  public void setTags(Set<String> tags) { this.tags = tags != null ? new HashSet<>(tags) : new HashSet<>(); }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JournalEntryEntity that)) return false;
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() { return getClass().hashCode(); }
}
```

- [ ] **Step 3.2: Create `JournalGoalEntity.java`**

```java
package com.austinharlan.trading_dashboard.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "journal_goals")
public class JournalGoalEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "label", nullable = false)
  private String label;

  @Column(name = "goal_type", nullable = false, length = 16)
  private String goalType;

  @Column(name = "target_value")
  private BigDecimal targetValue;

  @Column(name = "milestone_value")
  private BigDecimal milestoneValue;

  @Column(name = "deadline")
  private LocalDate deadline;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected JournalGoalEntity() {}

  public JournalGoalEntity(
      String label, String goalType, BigDecimal targetValue, BigDecimal milestoneValue, LocalDate deadline) {
    this.label = Objects.requireNonNull(label, "label must not be null");
    this.goalType = Objects.requireNonNull(goalType, "goalType must not be null");
    this.targetValue = targetValue;
    this.milestoneValue = milestoneValue;
    this.deadline = deadline;
    this.createdAt = Instant.now();
  }

  public Long getId() { return id; }
  public String getLabel() { return label; }
  public void setLabel(String label) { this.label = Objects.requireNonNull(label); }
  public String getGoalType() { return goalType; }
  public BigDecimal getTargetValue() { return targetValue; }
  public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }
  public BigDecimal getMilestoneValue() { return milestoneValue; }
  public void setMilestoneValue(BigDecimal milestoneValue) { this.milestoneValue = milestoneValue; }
  public LocalDate getDeadline() { return deadline; }
  public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
  public Instant getCreatedAt() { return createdAt; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JournalGoalEntity that)) return false;
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() { return getClass().hashCode(); }
}
```

- [ ] **Step 3.3: Verify compilation**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3.4: Commit**

```bash
git add src/main/java/com/austinharlan/trading_dashboard/persistence/JournalEntryEntity.java \
        src/main/java/com/austinharlan/trading_dashboard/persistence/JournalGoalEntity.java
git commit -m "feat: add JournalEntryEntity and JournalGoalEntity JPA entities"
```

---

### Task 4: Repositories

**Files:**
- Create: `src/main/java/com/austinharlan/trading_dashboard/persistence/JournalEntryRepository.java`
- Create: `src/main/java/com/austinharlan/trading_dashboard/persistence/JournalGoalRepository.java`

- [ ] **Step 4.1: Create `JournalEntryRepository.java`**

```java
package com.austinharlan.trading_dashboard.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, Long> {

  List<JournalEntryEntity> findAllByOrderByEntryDateDesc();

  Optional<JournalEntryEntity> findByEntryDate(LocalDate entryDate);

  @Query(
      """
      select distinct e from JournalEntryEntity e
      join e.tickers t
      where t = :ticker
      order by e.entryDate desc
      """)
  List<JournalEntryEntity> findByTicker(@Param("ticker") String ticker);

  @Query(
      """
      select distinct e from JournalEntryEntity e
      join e.tags t
      where t = :tag
      order by e.entryDate desc
      """)
  List<JournalEntryEntity> findByTag(@Param("tag") String tag);

  /** All entry dates ascending — used for streak calculation. */
  @Query("select e.entryDate from JournalEntryEntity e order by e.entryDate asc")
  List<LocalDate> findAllEntryDatesAsc();

  /** Ticker -> count of entries mentioning it. */
  @Query(
      """
      select t, count(e) from JournalEntryEntity e
      join e.tickers t
      group by t
      order by count(e) desc
      """)
  List<Object[]> countByTicker();

  /** Tag -> count of entries mentioning it. */
  @Query(
      """
      select t, count(e) from JournalEntryEntity e
      join e.tags t
      group by t
      order by count(e) desc
      """)
  List<Object[]> countByTag();

  /** Count of distinct entry_date values in a given calendar month. Used for habit goal progress. */
  @Query(
      """
      select count(distinct e.entryDate) from JournalEntryEntity e
      where year(e.entryDate) = :year and month(e.entryDate) = :month
      """)
  long countDistinctEntryDatesInMonth(@Param("year") int year, @Param("month") int month);
}
```

- [ ] **Step 4.2: Create `JournalGoalRepository.java`**

```java
package com.austinharlan.trading_dashboard.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalGoalRepository extends JpaRepository<JournalGoalEntity, Long> {}
```

- [ ] **Step 4.3: Verify compilation**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.4: Commit**

```bash
git add src/main/java/com/austinharlan/trading_dashboard/persistence/JournalEntryRepository.java \
        src/main/java/com/austinharlan/trading_dashboard/persistence/JournalGoalRepository.java
git commit -m "feat: add JournalEntryRepository and JournalGoalRepository"
```

---

### Task 5: JournalService

**Files:**
- Create: `src/main/java/com/austinharlan/trading_dashboard/service/JournalService.java`
- Create: `src/main/java/com/austinharlan/trading_dashboard/service/DefaultJournalService.java`

- [ ] **Step 5.1: Create the service interface `JournalService.java`**

```java
package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.persistence.JournalEntryEntity;
import com.austinharlan.trading_dashboard.persistence.JournalGoalEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

public interface JournalService {

  List<JournalEntryEntity> listEntries(
      @Nullable String ticker, @Nullable String tag, @Nullable LocalDate entryDate);

  JournalEntryEntity createEntry(String body, LocalDate entryDate);

  JournalEntryEntity updateEntry(long id, String body);

  void deleteEntry(long id);

  List<GoalWithProgress> listGoals();

  JournalGoalEntity createGoal(
      String label,
      String goalType,
      @Nullable BigDecimal targetValue,
      @Nullable BigDecimal milestoneValue,
      @Nullable LocalDate deadline);

  JournalGoalEntity updateGoal(
      long id,
      String label,
      @Nullable BigDecimal targetValue,
      @Nullable BigDecimal milestoneValue,
      @Nullable LocalDate deadline);

  void deleteGoal(long id);

  JournalStats getStats();

  /** Goal entity paired with its computed progress. */
  record GoalWithProgress(
      JournalGoalEntity goal, BigDecimal currentProgress, @Nullable Double progressPct) {}

  /** Stats response data. */
  record JournalStats(
      long entryCount,
      int currentStreak,
      Map<LocalDate, Integer> calendar,
      List<TokenCount> mostMentioned) {
    public record TokenCount(String token, int count) {}
  }
}
```

- [ ] **Step 5.2: Create `DefaultJournalService.java`**

```java
package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.persistence.JournalEntryEntity;
import com.austinharlan.trading_dashboard.persistence.JournalEntryRepository;
import com.austinharlan.trading_dashboard.persistence.JournalGoalEntity;
import com.austinharlan.trading_dashboard.persistence.JournalGoalRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultJournalService implements JournalService {

  private static final Pattern TICKER_PATTERN = Pattern.compile("\\$([A-Z]{1,10})");
  private static final Pattern TAG_PATTERN = Pattern.compile("#([a-zA-Z][a-zA-Z0-9_]*)");

  private final JournalEntryRepository entryRepository;
  private final JournalGoalRepository goalRepository;

  public DefaultJournalService(
      JournalEntryRepository entryRepository, JournalGoalRepository goalRepository) {
    this.entryRepository = entryRepository;
    this.goalRepository = goalRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<JournalEntryEntity> listEntries(
      @Nullable String ticker, @Nullable String tag, @Nullable LocalDate entryDate) {
    if (entryDate != null) {
      return entryRepository.findByEntryDate(entryDate).map(List::of).orElse(List.of());
    }
    if (ticker != null) {
      return entryRepository.findByTicker(ticker.toUpperCase());
    }
    if (tag != null) {
      return entryRepository.findByTag(tag.toLowerCase());
    }
    return entryRepository.findAllByOrderByEntryDateDesc();
  }

  @Override
  public JournalEntryEntity createEntry(String body, LocalDate entryDate) {
    Set<String> tickers = extractTickers(body);
    Set<String> tags = extractTags(body);
    JournalEntryEntity entry = new JournalEntryEntity(body, entryDate, tickers, tags);
    return entryRepository.save(entry);
  }

  @Override
  public JournalEntryEntity updateEntry(long id, String body) {
    JournalEntryEntity entry =
        entryRepository.findById(id).orElseThrow(() -> notFound("Entry", id));
    entry.setBody(body);
    entry.setTickers(extractTickers(body));
    entry.setTags(extractTags(body));
    entry.setUpdatedAt(Instant.now());
    return entryRepository.save(entry);
  }

  @Override
  public void deleteEntry(long id) {
    if (!entryRepository.existsById(id)) throw notFound("Entry", id);
    entryRepository.deleteById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public List<GoalWithProgress> listGoals() {
    YearMonth currentMonth = YearMonth.now();
    long habitProgress =
        entryRepository.countDistinctEntryDatesInMonth(
            currentMonth.getYear(), currentMonth.getMonthValue());

    return goalRepository.findAll().stream()
        .map(goal -> toGoalWithProgress(goal, habitProgress))
        .toList();
  }

  @Override
  public JournalGoalEntity createGoal(
      String label,
      String goalType,
      @Nullable BigDecimal targetValue,
      @Nullable BigDecimal milestoneValue,
      @Nullable LocalDate deadline) {
    JournalGoalEntity goal =
        new JournalGoalEntity(label, goalType, targetValue, milestoneValue, deadline);
    return goalRepository.save(goal);
  }

  @Override
  public JournalGoalEntity updateGoal(
      long id,
      String label,
      @Nullable BigDecimal targetValue,
      @Nullable BigDecimal milestoneValue,
      @Nullable LocalDate deadline) {
    JournalGoalEntity goal = goalRepository.findById(id).orElseThrow(() -> notFound("Goal", id));
    goal.setLabel(label);
    goal.setTargetValue(targetValue);
    goal.setMilestoneValue(milestoneValue);
    goal.setDeadline(deadline);
    return goalRepository.save(goal);
  }

  @Override
  public void deleteGoal(long id) {
    if (!goalRepository.existsById(id)) throw notFound("Goal", id);
    goalRepository.deleteById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public JournalStats getStats() {
    List<JournalEntryEntity> allEntries = entryRepository.findAllByOrderByEntryDateDesc();
    long entryCount = allEntries.size();
    int streak = computeStreak(entryRepository.findAllEntryDatesAsc());
    Map<LocalDate, Integer> calendar = buildCalendar(allEntries);
    List<JournalStats.TokenCount> mostMentioned = buildMostMentioned();
    return new JournalStats(entryCount, streak, calendar, mostMentioned);
  }

  // ── private helpers ──────────────────────────────────────────────────────

  private static Set<String> extractTickers(String html) {
    String text = html.replaceAll("<[^>]+>", " ");
    Matcher m = TICKER_PATTERN.matcher(text);
    Set<String> result = new java.util.LinkedHashSet<>();
    while (m.find()) result.add(m.group(1));
    return result;
  }

  private static Set<String> extractTags(String html) {
    String text = html.replaceAll("<[^>]+>", " ");
    Matcher m = TAG_PATTERN.matcher(text);
    Set<String> result = new java.util.LinkedHashSet<>();
    while (m.find()) result.add(m.group(1).toLowerCase());
    return result;
  }

  private static int computeStreak(List<LocalDate> datesAsc) {
    if (datesAsc.isEmpty()) return 0;
    Set<LocalDate> dateSet = new java.util.HashSet<>(datesAsc);
    int streak = 0;
    LocalDate check = LocalDate.now();
    while (dateSet.contains(check)) {
      streak++;
      check = check.minusDays(1);
    }
    return streak;
  }

  private static Map<LocalDate, Integer> buildCalendar(List<JournalEntryEntity> entries) {
    Map<LocalDate, Integer> calendar = new LinkedHashMap<>();
    // Populate the current month with all days at level 0
    YearMonth month = YearMonth.now();
    for (int day = 1; day <= month.lengthOfMonth(); day++) {
      calendar.put(month.atDay(day), 0);
    }
    // Override days that have entries
    for (JournalEntryEntity e : entries) {
      if (calendar.containsKey(e.getEntryDate())) {
        int level = isRichEntry(e) ? 2 : 1;
        calendar.put(e.getEntryDate(), level);
      }
    }
    return calendar;
  }

  /**
   * Activity level 2: entry contains an img/blockquote (media embed) OR 3+ tickers+tags combined.
   */
  private static boolean isRichEntry(JournalEntryEntity e) {
    boolean hasMedia = e.getBody().contains("<img") || e.getBody().contains("<blockquote");
    boolean hasThreePlusTags = (e.getTickers().size() + e.getTags().size()) >= 3;
    return hasMedia || hasThreePlusTags;
  }

  private List<JournalStats.TokenCount> buildMostMentioned() {
    List<JournalStats.TokenCount> counts = new ArrayList<>();
    entryRepository.countByTicker().forEach(row -> counts.add(
        new JournalStats.TokenCount("$" + row[0], ((Number) row[1]).intValue())));
    entryRepository.countByTag().forEach(row -> counts.add(
        new JournalStats.TokenCount("#" + row[0], ((Number) row[1]).intValue())));
    counts.sort((a, b) -> Integer.compare(b.count(), a.count()));
    return counts.stream().limit(10).toList();
  }

  private static GoalWithProgress toGoalWithProgress(JournalGoalEntity goal, long habitProgress) {
    BigDecimal progress;
    if ("habit".equals(goal.getGoalType())) {
      progress = BigDecimal.valueOf(habitProgress);
    } else {
      progress = goal.getMilestoneValue();
    }
    Double progressPct = null;
    if (progress != null && goal.getTargetValue() != null
        && goal.getTargetValue().compareTo(BigDecimal.ZERO) > 0) {
      progressPct =
          progress
              .divide(goal.getTargetValue(), 4, RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(100))
              .doubleValue();
    }
    return new GoalWithProgress(goal, progress, progressPct);
  }

  private static EntityNotFoundException notFound(String type, long id) {
    return new EntityNotFoundException(type + " not found: " + id);
  }
}
```

- [ ] **Step 5.3: Verify compilation**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.4: Format and commit**

```bash
./gradlew spotlessApply
git add src/main/java/com/austinharlan/trading_dashboard/service/JournalService.java \
        src/main/java/com/austinharlan/trading_dashboard/service/DefaultJournalService.java
git commit -m "feat: add JournalService interface and DefaultJournalService implementation"
```

---

### Task 6: JournalController

**Files:**
- Create: `src/main/java/com/austinharlan/trading_dashboard/controllers/JournalController.java`

Note: `EntityNotFoundException` must be mapped to 404. Check `ApiExceptionHandler` — if it already handles `EntityNotFoundException`, no change needed. If not, add a handler there.

- [ ] **Step 6.1: Check `ApiExceptionHandler.java` for `EntityNotFoundException` handling**

Open `src/main/java/com/austinharlan/trading_dashboard/controllers/ApiExceptionHandler.java`. If a `@ExceptionHandler(EntityNotFoundException.class)` method exists, skip Step 6.2.

- [ ] **Step 6.2: (Conditional) Add `EntityNotFoundException` handler to `ApiExceptionHandler`**

If not already present, add inside `ApiExceptionHandler`:

```java
@ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
public ResponseEntity<ErrorResponse> handleEntityNotFound(
    jakarta.persistence.EntityNotFoundException ex) {
  return ResponseEntity.status(HttpStatus.NOT_FOUND)
      .body(new ErrorResponse().code("NOT_FOUND").message(ex.getMessage()));
}
```

- [ ] **Step 6.3: Create `JournalController.java`**

```java
package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.persistence.JournalEntryEntity;
import com.austinharlan.trading_dashboard.persistence.JournalGoalEntity;
import com.austinharlan.trading_dashboard.service.JournalService;
import com.austinharlan.trading_dashboard.service.JournalService.GoalWithProgress;
import com.austinharlan.trading_dashboard.service.JournalService.JournalStats;
import com.austinharlan.tradingdashboard.api.JournalApi;
import com.austinharlan.tradingdashboard.dto.JournalEntryRequest;
import com.austinharlan.tradingdashboard.dto.JournalEntryResponse;
import com.austinharlan.tradingdashboard.dto.JournalGoalRequest;
import com.austinharlan.tradingdashboard.dto.JournalGoalResponse;
import com.austinharlan.tradingdashboard.dto.JournalStatsResponse;
import com.austinharlan.tradingdashboard.dto.JournalTokenCount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JournalController implements JournalApi {

  private final JournalService journalService;

  public JournalController(JournalService journalService) {
    this.journalService = journalService;
  }

  @Override
  public ResponseEntity<List<JournalEntryResponse>> listJournalEntries(
      String ticker, String tag, LocalDate entryDate) {
    List<JournalEntryEntity> entries = journalService.listEntries(ticker, tag, entryDate);
    return ResponseEntity.ok(entries.stream().map(this::toEntryResponse).toList());
  }

  @Override
  public ResponseEntity<JournalEntryResponse> createJournalEntry(JournalEntryRequest request) {
    JournalEntryEntity entry =
        journalService.createEntry(request.getBody(), request.getEntryDate());
    return ResponseEntity.status(201).body(toEntryResponse(entry));
  }

  @Override
  public ResponseEntity<JournalEntryResponse> updateJournalEntry(
      Long id, JournalEntryRequest request) {
    JournalEntryEntity entry = journalService.updateEntry(id, request.getBody());
    return ResponseEntity.ok(toEntryResponse(entry));
  }

  @Override
  public ResponseEntity<Void> deleteJournalEntry(Long id) {
    journalService.deleteEntry(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<List<JournalGoalResponse>> listJournalGoals() {
    return ResponseEntity.ok(
        journalService.listGoals().stream().map(this::toGoalResponse).toList());
  }

  @Override
  public ResponseEntity<JournalGoalResponse> createJournalGoal(JournalGoalRequest request) {
    JournalGoalEntity goal =
        journalService.createGoal(
            request.getLabel(),
            request.getGoalType().getValue(),
            toDecimal(request.getTargetValue()),
            toDecimal(request.getMilestoneValue()),
            request.getDeadline());
    // re-fetch with progress for the response
    GoalWithProgress gwp = journalService.listGoals().stream()
        .filter(g -> g.goal().getId().equals(goal.getId()))
        .findFirst()
        .orElseThrow();
    return ResponseEntity.status(201).body(toGoalResponse(gwp));
  }

  @Override
  public ResponseEntity<JournalGoalResponse> updateJournalGoal(
      Long id, JournalGoalRequest request) {
    journalService.updateGoal(
        id,
        request.getLabel(),
        toDecimal(request.getTargetValue()),
        toDecimal(request.getMilestoneValue()),
        request.getDeadline());
    GoalWithProgress gwp = journalService.listGoals().stream()
        .filter(g -> g.goal().getId().equals(id))
        .findFirst()
        .orElseThrow();
    return ResponseEntity.ok(toGoalResponse(gwp));
  }

  @Override
  public ResponseEntity<Void> deleteJournalGoal(Long id) {
    journalService.deleteGoal(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<JournalStatsResponse> getJournalStats() {
    JournalStats stats = journalService.getStats();
    Map<String, Integer> calendarMap = stats.calendar().entrySet().stream()
        .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
    List<JournalTokenCount> tokenCounts = stats.mostMentioned().stream()
        .map(tc -> new JournalTokenCount().token(tc.token()).count(tc.count()))
        .toList();
    JournalStatsResponse response = new JournalStatsResponse()
        .entryCount(stats.entryCount())
        .currentStreak(stats.currentStreak())
        .calendar(calendarMap)
        .mostMentioned(tokenCounts);
    return ResponseEntity.ok(response);
  }

  // ── private mappers ───────────────────────────────────────────────────────

  private JournalEntryResponse toEntryResponse(JournalEntryEntity e) {
    return new JournalEntryResponse()
        .id(e.getId())
        .body(e.getBody())
        .entryDate(e.getEntryDate())
        .tickers(List.copyOf(e.getTickers()))
        .tags(List.copyOf(e.getTags()))
        .createdAt(OffsetDateTime.ofInstant(e.getCreatedAt(), ZoneOffset.UTC))
        .updatedAt(OffsetDateTime.ofInstant(e.getUpdatedAt(), ZoneOffset.UTC));
  }

  private JournalGoalResponse toGoalResponse(GoalWithProgress gwp) {
    JournalGoalEntity g = gwp.goal();
    return new JournalGoalResponse()
        .id(g.getId())
        .label(g.getLabel())
        .goalType(JournalGoalResponse.GoalTypeEnum.fromValue(g.getGoalType()))
        .targetValue(g.getTargetValue() != null ? g.getTargetValue().doubleValue() : null)
        .currentProgress(gwp.currentProgress() != null ? gwp.currentProgress().doubleValue() : null)
        .progressPct(gwp.progressPct())
        .deadline(g.getDeadline())
        .createdAt(OffsetDateTime.ofInstant(g.getCreatedAt(), ZoneOffset.UTC));
  }

  private static BigDecimal toDecimal(Double value) {
    return value != null ? BigDecimal.valueOf(value) : null;
  }
}
```

- [ ] **Step 6.4: Build to confirm no compilation errors**

```bash
./gradlew build -x test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6.5: Format and commit**

```bash
./gradlew spotlessApply
git add src/main/java/com/austinharlan/trading_dashboard/controllers/JournalController.java \
        src/main/java/com/austinharlan/trading_dashboard/controllers/ApiExceptionHandler.java
git commit -m "feat: add JournalController wiring OpenAPI interface to JournalService"
```

---

## Chunk 2: Tests + Frontend

---

### Task 7: Unit Tests — DefaultJournalService

**Files:**
- Create: `src/test/java/com/austinharlan/trading_dashboard/service/DefaultJournalServiceTest.java`

These are pure unit tests — no Spring context, no DB. Repositories are mocked.

- [ ] **Step 7.1: Write failing tests first, then run**

```java
package com.austinharlan.trading_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.austinharlan.trading_dashboard.persistence.JournalEntryEntity;
import com.austinharlan.trading_dashboard.persistence.JournalEntryRepository;
import com.austinharlan.trading_dashboard.persistence.JournalGoalEntity;
import com.austinharlan.trading_dashboard.persistence.JournalGoalRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultJournalServiceTest {

  private JournalEntryRepository entryRepo;
  private JournalGoalRepository goalRepo;
  private DefaultJournalService service;

  @BeforeEach
  void setUp() {
    entryRepo = mock(JournalEntryRepository.class);
    goalRepo = mock(JournalGoalRepository.class);
    service = new DefaultJournalService(entryRepo, goalRepo);
  }

  // ── createEntry: ticker/tag extraction ───────────────────────────────────

  @Test
  void createEntry_extractsTickersFromHtml() {
    String html = "<p><span class=\"neon-token\">$NVDA</span> and <span>$MSFT</span></p>";
    LocalDate today = LocalDate.now();
    when(entryRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

    JournalEntryEntity entry = service.createEntry(html, today);

    assertThat(entry.getTickers()).containsExactlyInAnyOrder("NVDA", "MSFT");
  }

  @Test
  void createEntry_extractsTagsFromHtml() {
    String html = "<p>Watching #macro and #growth plays today.</p>";
    when(entryRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

    JournalEntryEntity entry = service.createEntry(html, LocalDate.now());

    assertThat(entry.getTags()).containsExactlyInAnyOrder("macro", "growth");
  }

  @Test
  void createEntry_deduplicatesRepeatedTokens() {
    String html = "<p>$NVDA up, $NVDA strong, still holding $NVDA</p>";
    when(entryRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

    JournalEntryEntity entry = service.createEntry(html, LocalDate.now());

    assertThat(entry.getTickers()).containsExactly("NVDA");
  }

  @Test
  void createEntry_ignoresHtmlTagsWhenExtractingTokens() {
    // HTML tags like <strong> should not be mistaken for $TICKER patterns
    String html = "<strong>Bold text</strong> $AAPL only";
    when(entryRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

    JournalEntryEntity entry = service.createEntry(html, LocalDate.now());

    assertThat(entry.getTickers()).containsExactly("AAPL");
  }

  // ── updateEntry ───────────────────────────────────────────────────────────

  @Test
  void updateEntry_throwsWhenNotFound() {
    when(entryRepo.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.updateEntry(99L, "<p>text</p>"))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("99");
  }

  @Test
  void updateEntry_refreshesTickersAndTags() {
    JournalEntryEntity existing =
        new JournalEntryEntity("<p>$TSLA</p>", LocalDate.now(), Set.of("TSLA"), Set.of());
    when(entryRepo.findById(1L)).thenReturn(Optional.of(existing));
    when(entryRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

    JournalEntryEntity updated = service.updateEntry(1L, "<p>$NVDA #macro</p>");

    assertThat(updated.getTickers()).containsExactly("NVDA");
    assertThat(updated.getTags()).containsExactly("macro");
  }

  // ── deleteEntry ───────────────────────────────────────────────────────────

  @Test
  void deleteEntry_throwsWhenNotFound() {
    when(entryRepo.existsById(5L)).thenReturn(false);

    assertThatThrownBy(() -> service.deleteEntry(5L))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void deleteEntry_delegatesToRepository() {
    when(entryRepo.existsById(3L)).thenReturn(true);

    service.deleteEntry(3L);

    verify(entryRepo).deleteById(3L);
  }

  // ── getStats: streak ─────────────────────────────────────────────────────

  @Test
  void getStats_streakIsZeroWhenNoEntries() {
    when(entryRepo.findAllByOrderByEntryDateDesc()).thenReturn(List.of());
    when(entryRepo.findAllEntryDatesAsc()).thenReturn(List.of());
    when(entryRepo.countByTicker()).thenReturn(List.of());
    when(entryRepo.countByTag()).thenReturn(List.of());

    JournalService.JournalStats stats = service.getStats();

    assertThat(stats.currentStreak()).isZero();
  }

  @Test
  void getStats_streakCountsConsecutiveDaysBackFromToday() {
    LocalDate today = LocalDate.now();
    LocalDate yesterday = today.minusDays(1);
    when(entryRepo.findAllByOrderByEntryDateDesc()).thenReturn(List.of());
    when(entryRepo.findAllEntryDatesAsc()).thenReturn(List.of(yesterday, today));
    when(entryRepo.countByTicker()).thenReturn(List.of());
    when(entryRepo.countByTag()).thenReturn(List.of());

    JournalService.JournalStats stats = service.getStats();

    assertThat(stats.currentStreak()).isEqualTo(2);
  }

  @Test
  void getStats_streakBreaksOnGap() {
    LocalDate today = LocalDate.now();
    LocalDate twoDaysAgo = today.minusDays(2); // yesterday is missing
    when(entryRepo.findAllByOrderByEntryDateDesc()).thenReturn(List.of());
    when(entryRepo.findAllEntryDatesAsc()).thenReturn(List.of(twoDaysAgo, today));
    when(entryRepo.countByTicker()).thenReturn(List.of());
    when(entryRepo.countByTag()).thenReturn(List.of());

    JournalService.JournalStats stats = service.getStats();

    // today is present → streak = 1 (gap before today breaks the chain)
    assertThat(stats.currentStreak()).isEqualTo(1);
  }

  // ── goal progress ─────────────────────────────────────────────────────────

  @Test
  void listGoals_milestoneProgressUsesStoredMilestoneValue() {
    JournalGoalEntity goal =
        new JournalGoalEntity(
            "Hit $500K", "milestone", BigDecimal.valueOf(500000), BigDecimal.valueOf(372000), null);
    when(goalRepo.findAll()).thenReturn(List.of(goal));
    when(entryRepo.countDistinctEntryDatesInMonth(
            org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(0L);

    JournalService.GoalWithProgress gwp = service.listGoals().getFirst();

    assertThat(gwp.currentProgress()).isEqualByComparingTo(BigDecimal.valueOf(372000));
    assertThat(gwp.progressPct()).isCloseTo(74.4, org.assertj.core.data.Offset.offset(0.1));
  }

  @Test
  void listGoals_habitProgressUsesEntryCountForCurrentMonth() {
    JournalGoalEntity goal =
        new JournalGoalEntity("Write daily", "habit", BigDecimal.valueOf(30), null, null);
    when(goalRepo.findAll()).thenReturn(List.of(goal));
    when(entryRepo.countDistinctEntryDatesInMonth(
            org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(12L);

    JournalService.GoalWithProgress gwp = service.listGoals().getFirst();

    assertThat(gwp.currentProgress()).isEqualByComparingTo(BigDecimal.valueOf(12));
    assertThat(gwp.progressPct()).isCloseTo(40.0, org.assertj.core.data.Offset.offset(0.1));
  }
}
```

Note: The `Set.of()` call in `updateEntry_refreshesTickersAndTags` requires the import `java.util.Set`.

- [ ] **Step 7.2: Run tests to verify they fail (expected — no compilation issues just missing the import)**

```bash
./gradlew test --tests "*.DefaultJournalServiceTest"
```

Expected: Some tests compile and pass already (extraction tests), but confirm no unexpected errors.

- [ ] **Step 7.3: Run full test suite to ensure nothing else broke**

```bash
./gradlew test
```

Expected: All previously passing tests still pass. New tests pass.

- [ ] **Step 7.4: Commit**

```bash
git add src/test/java/com/austinharlan/trading_dashboard/service/DefaultJournalServiceTest.java
git commit -m "test: add DefaultJournalServiceTest unit tests"
```

---

### Task 8: Integration Tests — JournalRepositoryIT

**Files:**
- Create: `src/test/java/com/austinharlan/trading_dashboard/persistence/JournalRepositoryIT.java`

These tests extend `DatabaseIntegrationTest` and use Testcontainers (requires Docker). They're skipped if Docker is unavailable.

- [ ] **Step 8.1: Write the integration test**

```java
package com.austinharlan.trading_dashboard.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JournalRepositoryIT extends DatabaseIntegrationTest {

  @Autowired private JournalEntryRepository entryRepo;
  @Autowired private JournalGoalRepository goalRepo;

  @AfterEach
  void cleanup() {
    entryRepo.deleteAll();
    goalRepo.deleteAll();
  }

  @Test
  void flywayCreatesJournalTables() {
    assertThat(entryRepo.findAll()).isEmpty();
    assertThat(goalRepo.findAll()).isEmpty();
  }

  @Test
  void saveAndFindEntryByDate() {
    LocalDate target = LocalDate.of(2026, 3, 15);
    JournalEntryEntity entry =
        new JournalEntryEntity("<p>$NVDA</p>", target, Set.of("NVDA"), Set.of("macro"));
    entryRepo.save(entry);

    Optional<JournalEntryEntity> found = entryRepo.findByEntryDate(target);

    assertThat(found).isPresent();
    assertThat(found.get().getTickers()).containsExactly("NVDA");
    assertThat(found.get().getTags()).containsExactly("macro");
  }

  @Test
  void findByTicker_returnsOnlyMatchingEntries() {
    entryRepo.save(new JournalEntryEntity("<p>$NVDA</p>",
        LocalDate.of(2026, 3, 1), Set.of("NVDA"), Set.of()));
    entryRepo.save(new JournalEntryEntity("<p>$MSFT</p>",
        LocalDate.of(2026, 3, 2), Set.of("MSFT"), Set.of()));

    List<JournalEntryEntity> results = entryRepo.findByTicker("NVDA");

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getTickers()).contains("NVDA");
  }

  @Test
  void findByTag_returnsOnlyMatchingEntries() {
    entryRepo.save(new JournalEntryEntity("<p>#macro</p>",
        LocalDate.of(2026, 3, 1), Set.of(), Set.of("macro")));
    entryRepo.save(new JournalEntryEntity("<p>#growth</p>",
        LocalDate.of(2026, 3, 2), Set.of(), Set.of("growth")));

    List<JournalEntryEntity> results = entryRepo.findByTag("macro");

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getTags()).contains("macro");
  }

  @Test
  void deletingEntry_cascadesToTickersAndTags() {
    JournalEntryEntity entry = entryRepo.save(
        new JournalEntryEntity("<p>$TSLA #ev</p>",
            LocalDate.of(2026, 3, 3), Set.of("TSLA"), Set.of("ev")));

    entryRepo.deleteById(entry.getId());

    // If cascade works, the entry is gone; the child rows are removed automatically.
    assertThat(entryRepo.findById(entry.getId())).isEmpty();
  }

  @Test
  void countDistinctEntryDatesInMonth() {
    entryRepo.save(new JournalEntryEntity("<p>a</p>", LocalDate.of(2026, 3, 1), Set.of(), Set.of()));
    entryRepo.save(new JournalEntryEntity("<p>b</p>", LocalDate.of(2026, 3, 5), Set.of(), Set.of()));
    entryRepo.save(new JournalEntryEntity("<p>c</p>", LocalDate.of(2026, 4, 1), Set.of(), Set.of()));

    long marchCount = entryRepo.countDistinctEntryDatesInMonth(2026, 3);

    assertThat(marchCount).isEqualTo(2);
  }

  @Test
  void uniqueConstraint_preventsSecondEntryForSameDate() {
    entryRepo.save(new JournalEntryEntity("<p>first</p>",
        LocalDate.of(2026, 3, 10), Set.of(), Set.of()));

    JournalEntryEntity duplicate =
        new JournalEntryEntity("<p>second</p>", LocalDate.of(2026, 3, 10), Set.of(), Set.of());

    org.junit.jupiter.api.Assertions.assertThrows(
        Exception.class, () -> {
          entryRepo.save(duplicate);
          entryRepo.flush(); // force the constraint check
        });
  }
}
```

- [ ] **Step 8.2: Run the integration test (requires Docker)**

```bash
./gradlew test --tests "*.JournalRepositoryIT"
```

Expected: PASS (or skipped if Docker unavailable locally; CI will run it).

- [ ] **Step 8.3: Commit**

```bash
git add src/test/java/com/austinharlan/trading_dashboard/persistence/JournalRepositoryIT.java
git commit -m "test: add JournalRepositoryIT integration tests"
```

---

### Task 9: Frontend — Nav Item, Page Skeleton, loadJournal()

**Files:**
- Modify: `src/main/resources/static/index.html`

> This is the largest task. Read `index.html` fully before editing. Make one targeted change at a time to avoid disrupting the existing 1500+ line file.

- [ ] **Step 9.1: Add the Journal nav item to the sidebar**

Locate the sidebar `<nav class="sidebar-nav">` block. After the Finance `nav-item` (the last item before `</nav>`), add:

```html
      <div class="nav-item" data-page="journal">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.4">
          <rect x="2" y="1" width="10" height="12" rx="1.5"/>
          <path d="M5 4h4M5 7h4M5 10h2"/>
        </svg>
        <span class="nav-label">Journal</span>
      </div>
```

- [ ] **Step 9.2: Register page title**

Find `const TITLES = { dashboard: 'Dashboard', quotes: 'Quotes', portfolio: 'Portfolio', finance: 'Finance' };` and add `journal: 'Journal'` to the object.

- [ ] **Step 9.3: Register page-load hook**

Find the block that contains `if (page === 'dashboard') loadDashboard();` etc. Add after the last one:

```javascript
    if (page === 'journal') loadJournal();
```

- [ ] **Step 9.4: Add the page container**

After the last `<div class="page" ...>...</div>` block (Finance), add the journal page stub:

```html
      <!-- Journal -->
      <div class="page" id="page-journal"></div>
```

- [ ] **Step 9.5: Add Inter font link and journal CSS**

In `<head>`, after the last existing `<link>` or `<meta>` tag, add:

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:opsz,wght@14..32,300;14..32,400&display=swap" rel="stylesheet">
```

(`@import` inside a `<style>` block is ignored when not the first rule — a `<link>` tag is the correct approach.)

Then, inside the `<style>` block, append the following CSS (before the closing `</style>`):

```css
    /* ── Journal ─────────────────────────────────────────── */

    .j-layout { display: grid; grid-template-columns: 60fr 40fr; gap: 16px; align-items: start; }
    .j-feed { display: flex; flex-direction: column; gap: 12px; }
    .j-sidebar { display: flex; flex-direction: column; gap: 12px; position: sticky; top: 16px; }

    /* Entry card */
    .j-card { background: var(--bg-card); border: 1px solid rgba(78,221,138,.22); border-radius: 8px; overflow: hidden; }
    .j-card.today { border-color: rgba(78,221,138,.30); }
    .j-card-head { padding: 10px 14px 0; display: flex; justify-content: space-between; align-items: center; }
    .j-card-date { font-size: 10px; letter-spacing: .8px; text-transform: uppercase; color: var(--text-dim); }
    .j-editor {
      background: #000;
      box-shadow: inset 0 1px 0 rgba(255,255,255,.04);
      font-family: 'Inter', sans-serif;
      font-size: 16px;
      font-weight: 300;
      line-height: 2.0;
      color: #c8c0b0;
      padding: 16px;
      min-height: 140px;
      outline: none;
      border: none;
    }
    .j-editor:empty::before {
      content: attr(data-placeholder);
      color: rgba(200,192,176,.18);
      pointer-events: none;
    }
    .j-card-foot { padding: 8px 14px; display: flex; justify-content: space-between; align-items: center; gap: 8px; }
    .j-pills { display: flex; flex-wrap: wrap; gap: 4px; }
    .j-pill { font-size: 10px; padding: 2px 7px; border-radius: 10px; background: rgba(78,221,138,.08); color: var(--green); }
    .j-save-btn { font-size: 11px; padding: 4px 12px; background: rgba(78,221,138,.12); border: 1px solid rgba(78,221,138,.22); border-radius: 4px; color: var(--green); cursor: pointer; letter-spacing: .4px; }
    .j-save-btn:hover { background: rgba(78,221,138,.2); }

    /* Past entry dimming */
    .j-card.age-1 { opacity: .55; }
    .j-card.age-2 { opacity: .28; }
    .j-card.age-old { opacity: .18; }

    /* Neon tokens */
    .neon-t-0  { color: #4edd8a; text-shadow: 0 0 8px rgba(78,221,138,.6); }
    .neon-t-1  { color: #00e5ff; text-shadow: 0 0 8px rgba(0,229,255,.6); }
    .neon-t-2  { color: #ff7b35; text-shadow: 0 0 8px rgba(255,123,53,.6); }
    .neon-t-3  { color: #b8ff3e; text-shadow: 0 0 8px rgba(184,255,62,.6); }
    .neon-t-4  { color: #ff3864; text-shadow: 0 0 8px rgba(255,56,100,.6); }
    .neon-t-5  { color: #a78bfa; text-shadow: 0 0 8px rgba(167,139,250,.6); }
    .neon-t-6  { color: #ff2d9e; text-shadow: 0 0 8px rgba(255,45,158,.6); }
    .neon-t-7  { color: #ffd23f; text-shadow: 0 0 8px rgba(255,210,63,.6); }
    .neon-t-8  { color: #b060ff; text-shadow: 0 0 8px rgba(176,96,255,.6); }
    .neon-t-9  { color: #f4b84a; text-shadow: 0 0 8px rgba(244,184,74,.6); }

    /* Filter banner */
    .j-filter-banner { background: rgba(78,221,138,.08); border: 1px solid rgba(78,221,138,.2); border-radius: 6px; padding: 6px 12px; display: flex; justify-content: space-between; align-items: center; font-size: 11px; color: var(--green); }
    .j-filter-clear { cursor: pointer; opacity: .6; }
    .j-filter-clear:hover { opacity: 1; }

    /* Sidebar widgets */
    .j-widget { background: var(--bg-card); border: 1px solid rgba(78,221,138,.22); border-radius: 8px; padding: 12px 14px; }
    .j-widget-title { font-size: 8px; font-weight: 500; letter-spacing: .9px; text-transform: uppercase; color: var(--text-dim); margin-bottom: 10px; }

    /* Calendar */
    .j-cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 3px; }
    .j-cal-head { font-size: 8px; color: var(--text-dim); text-align: center; padding-bottom: 4px; }
    .j-cal-day { aspect-ratio: 1; border-radius: 3px; cursor: default; }
    .j-cal-empty { background: transparent; }
    .j-cal-none { background: rgba(78,221,138,.06); }
    .j-cal-entry { background: rgba(78,221,138,.45); cursor: pointer; }
    .j-cal-rich { background: #4edd8a; box-shadow: 0 0 6px rgba(78,221,138,.5); cursor: pointer; }
    .j-cal-today { outline: 1px solid rgba(78,221,138,.6); outline-offset: 1px; }

    /* Stats row */
    .j-stats-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
    .j-stat { background: var(--bg-card); border: 1px solid rgba(78,221,138,.22); border-radius: 6px; padding: 8px 10px; text-align: center; }
    .j-stat-val { font-size: 18px; font-weight: 300; color: var(--text); font-variant-numeric: tabular-nums; line-height: 1.2; }
    .j-stat-lbl { font-size: 8px; letter-spacing: .7px; text-transform: uppercase; color: var(--text-dim); margin-top: 2px; }

    /* Goal progress bars */
    .j-goal { margin-bottom: 10px; }
    .j-goal:last-child { margin-bottom: 0; }
    .j-goal-label { font-size: 11px; color: var(--text-mid); margin-bottom: 4px; display: flex; justify-content: space-between; }
    .j-goal-track { height: 4px; background: rgba(78,221,138,.1); border-radius: 2px; overflow: hidden; }
    .j-goal-bar { height: 100%; border-radius: 2px; transition: width .4s; }
    .j-goal-bar.milestone { background: #ffd23f; box-shadow: 0 0 4px rgba(255,210,63,.4); }
    .j-goal-bar.habit    { background: #4edd8a; box-shadow: 0 0 4px rgba(78,221,138,.4); }

    /* Most written bar chart */
    .j-mw-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; font-size: 11px; }
    .j-mw-row:last-child { margin-bottom: 0; }
    .j-mw-token { min-width: 60px; cursor: pointer; }
    .j-mw-bar-wrap { flex: 1; height: 4px; background: rgba(78,221,138,.1); border-radius: 2px; overflow: hidden; }
    .j-mw-bar { height: 100%; background: rgba(78,221,138,.5); border-radius: 2px; }
    .j-mw-count { font-size: 10px; color: var(--text-dim); min-width: 20px; text-align: right; }

    /* Movers */
    .j-mover-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; font-size: 11px; }
    .j-mover-row:last-child { margin-bottom: 0; }
    .j-mover-ticker { min-width: 50px; font-variant-numeric: tabular-nums; cursor: pointer; }
    .j-mover-pct { flex: 1; font-variant-numeric: tabular-nums; }
    .j-mover-pct.up { color: var(--green); }
    .j-mover-pct.down { color: #ff3864; }
    .j-mover-count { font-size: 10px; color: var(--text-dim); }

    /* Goals edit mode */
    .j-goals-editbtn { font-size: 10px; padding: 2px 8px; background: transparent; border: 1px solid rgba(78,221,138,.22); border-radius: 4px; color: var(--text-dim); cursor: pointer; margin-left: auto; display: block; margin-bottom: 10px; }
    .j-goals-editbtn:hover { color: var(--green); border-color: rgba(78,221,138,.4); }
    .j-goal-inputs { display: none; margin-top: 8px; gap: 6px; }
    .j-goal-inputs.visible { display: flex; flex-wrap: wrap; }
    .j-goal-input { background: rgba(78,221,138,.06); border: 1px solid rgba(78,221,138,.18); border-radius: 4px; padding: 4px 8px; font-size: 11px; color: var(--text); width: 100%; }
    .j-goal-input:focus { outline: none; border-color: rgba(78,221,138,.4); }
    .j-add-goal-btn { font-size: 10px; color: var(--green); background: rgba(78,221,138,.08); border: 1px solid rgba(78,221,138,.2); border-radius: 4px; padding: 3px 10px; cursor: pointer; margin-top: 6px; }

    /* Media embed cards */
    .j-embed { background: rgba(78,221,138,.04); border: 1px solid rgba(78,221,138,.15); border-radius: 6px; padding: 8px; display: flex; gap: 10px; align-items: center; margin: 4px 0; user-select: none; }
    .j-embed-thumb { width: 80px; height: 45px; object-fit: cover; border-radius: 4px; flex-shrink: 0; }
    .j-embed-info { font-size: 11px; }
    .j-embed-source { font-size: 9px; color: var(--text-dim); margin-top: 2px; }
```

- [ ] **Step 9.6: Add `loadJournal()` stub and helper functions**

Find the JavaScript section (near `function loadDashboard()`) and add the following after the last `load*` function:

```javascript
  // ── Journal ───────────────────────────────────────────────────────────────

  const NEON_PALETTE = [
    '#4edd8a','#00e5ff','#ff7b35','#b8ff3e','#ff3864',
    '#a78bfa','#ff2d9e','#ffd23f','#b060ff','#f4b84a'
  ];

  function neonClass(token) {
    let h = 0;
    for (let i = 0; i < token.length; i++) h = (Math.imul(31, h) + token.charCodeAt(i)) | 0;
    return 'neon-t-' + (Math.abs(h) % 10);
  }

  function neonSpan(token) {
    const cls = neonClass(token.replace(/^[$#]/, '').toUpperCase());
    return `<span class="neon-token ${cls}" data-token="${token}">${token}</span>`;
  }

  let journalTodayId = null;   // null = no entry yet; number = existing entry id
  let journalFilter = null;    // null = unfiltered; string = active token filter
  let journalEntries = [];     // cached full list

  async function loadJournal() {
    const page = document.getElementById('page-journal');
    page.innerHTML = '<div class="state-box"><span class="spinner"></span></div>';
    try {
      const [entries, goals, stats] = await Promise.all([
        get('/api/journal/entries'),
        get('/api/journal/goals'),
        get('/api/journal/stats'),
      ]);
      journalEntries = entries || [];
      journalTodayId = null;
      const today = new Date().toISOString().slice(0, 10);
      const todayEntry = journalEntries.find(e => e.entry_date === today);
      if (todayEntry) journalTodayId = todayEntry.id;

      page.innerHTML = renderJournalLayout(todayEntry, journalEntries, goals, stats);
      attachJournalHandlers(goals, stats);
    } catch (e) {
      page.innerHTML = empty('📓', 'Journal unavailable', 'Could not load journal data.');
    }
  }
```

- [ ] **Step 9.7: Verify app still loads without errors (quick smoke test)**

```bash
./gradlew bootRun &
# Open http://localhost:8080 in browser, click Journal nav item
# Confirm: page renders without JS errors, spinner shows, no 404 on static assets
```

Kill the server after confirming (`kill %1` or Ctrl-C).

- [ ] **Step 9.8: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "feat: add journal nav item, page skeleton, CSS, and loadJournal stub"
```

---

### Task 10: Frontend — Editor, Token Detection, Save Flow

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 10.1: Add `renderJournalLayout()` and `renderTodayEditor()`**

Add these functions after `loadJournal()`:

```javascript
  function renderJournalLayout(todayEntry, entries, goals, stats) {
    return `
      <div class="j-layout">
        <div class="j-feed" id="j-feed">
          ${renderTodayEditor(todayEntry)}
          ${renderPastEntries(entries)}
        </div>
        <div class="j-sidebar">
          ${renderJStats(stats)}
          ${renderJCalendar(stats)}
          ${renderJMovers(entries)}
          ${renderJGoals(goals)}
          ${renderJMostWritten(stats)}
        </div>
      </div>`;
  }

  function renderTodayEditor(entry) {
    const today = new Date();
    const label = today.toLocaleDateString('en-US', { weekday:'long', month:'long', day:'numeric' });
    const content = entry ? colorizeHtml(entry.body) : '';
    const pills = entry ? renderPills([...entry.tickers.map(t=>'$'+t), ...entry.tags.map(t=>'#'+t)]) : '';
    return `
      <div class="j-card today" id="j-today-card">
        <div class="j-card-head">
          <span class="j-card-date">${label}</span>
        </div>
        <div class="j-editor"
             id="j-editor"
             contenteditable="true"
             data-placeholder="What's on your mind today?">${content}</div>
        <div class="j-card-foot">
          <div class="j-pills" id="j-today-pills">${pills}</div>
          <button class="j-save-btn" id="j-save-btn">Save</button>
        </div>
      </div>`;
  }

  function colorizeHtml(html) {
    // Replace $TICKER and #tag patterns in text nodes with neon spans
    return html
      .replace(/(?<![a-zA-Z0-9])(\$[A-Z]{1,10})(?![a-zA-Z0-9])/g,
        (_, t) => `<span class="neon-token ${neonClass(t.slice(1).toUpperCase())}" data-token="${t}">${t}</span>`)
      .replace(/(?<![a-zA-Z0-9])(#[a-zA-Z][a-zA-Z0-9_]*)(?![a-zA-Z0-9=])/g,
        (_, t) => `<span class="neon-token ${neonClass(t.slice(1).toUpperCase())}" data-token="${t}">${t}</span>`);
  }

  function renderPills(tokens) {
    return tokens.map(t => `<span class="j-pill">${t}</span>`).join('');
  }
```

- [ ] **Step 10.2: Add media embed detection and token-wrapping on input**

```javascript
  function attachJournalHandlers(goals, stats) {
    const editor = document.getElementById('j-editor');
    const saveBtn = document.getElementById('j-save-btn');
    if (!editor) return;

    // Token detection on input
    editor.addEventListener('input', () => {
      wrapTokensInEditor(editor);
      updateTodayPills(editor);
    });

    // Paste: strip HTML or detect embed URLs
    editor.addEventListener('paste', e => {
      e.preventDefault();
      const text = e.clipboardData.getData('text/plain').trim();
      const ytMatch = text.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/)([A-Za-z0-9_-]{11})/);
      const twMatch = text.match(/twitter\.com\/\S+\/status\/(\d+)|x\.com\/\S+\/status\/(\d+)/);
      if (ytMatch) {
        const vid = ytMatch[1];
        const card = `<div class="j-embed" contenteditable="false">` +
          `<img class="j-embed-thumb" src="https://img.youtube.com/vi/${vid}/mqdefault.jpg" alt="">` +
          `<div class="j-embed-info"><div>YouTube video</div>` +
          `<div class="j-embed-source">youtube.com</div></div></div>`;
        document.execCommand('insertHTML', false, card);
      } else if (twMatch) {
        const card = `<div class="j-embed" contenteditable="false">` +
          `<div class="j-embed-info"><div>𝕏 / Twitter post</div>` +
          `<div class="j-embed-source">x.com</div></div></div>`;
        document.execCommand('insertHTML', false, card);
      } else {
        document.execCommand('insertText', false, text);
      }
    });

    // Save
    saveBtn.addEventListener('click', () => saveEntry(editor));

    // Token clicks for filter
    document.getElementById('page-journal').addEventListener('click', e => {
      const t = e.target.closest('.neon-token');
      if (t) applyJournalFilter(t.dataset.token);
    });

    // Goals edit toggle
    const editGoalsBtn = document.getElementById('j-goals-edit-btn');
    if (editGoalsBtn) editGoalsBtn.addEventListener('click', toggleGoalsEdit);
  }

  function wrapTokensInEditor(editor) {
    // Walk text nodes only; skip existing .neon-token spans
    const sel = window.getSelection();
    const anchor = sel.anchorNode;
    const offset = sel.anchorOffset;

    const walker = document.createTreeWalker(editor, NodeFilter.SHOW_TEXT, {
      acceptNode: n => n.parentElement.classList.contains('neon-token')
        ? NodeFilter.FILTER_REJECT
        : NodeFilter.FILTER_ACCEPT
    });

    const textNodes = [];
    while (walker.nextNode()) textNodes.push(walker.currentNode);

    const TOKEN_RE = /(\$[A-Z]{1,10}|#[a-zA-Z][a-zA-Z0-9_]*)/g;
    textNodes.forEach(node => {
      const text = node.nodeValue;
      if (!TOKEN_RE.test(text)) return;
      TOKEN_RE.lastIndex = 0;
      const frag = document.createDocumentFragment();
      let last = 0, m;
      while ((m = TOKEN_RE.exec(text)) !== null) {
        if (m.index > last) frag.appendChild(document.createTextNode(text.slice(last, m.index)));
        const span = document.createElement('span');
        span.className = `neon-token ${neonClass(m[1].replace(/^[$#]/, '').toUpperCase())}`;
        span.dataset.token = m[1];
        span.textContent = m[1];
        frag.appendChild(span);
        last = m.index + m[0].length;
      }
      if (last < text.length) frag.appendChild(document.createTextNode(text.slice(last)));
      node.parentNode.replaceChild(frag, node);
    });

    // Restore caret
    try {
      if (anchor && anchor.isConnected) {
        const r = document.createRange();
        r.setStart(anchor, Math.min(offset, anchor.length || 0));
        r.collapse(true);
        sel.removeAllRanges();
        sel.addRange(r);
      }
    } catch (_) {}
  }

  function updateTodayPills(editor) {
    const html = editor.innerHTML;
    const tickers = [...html.matchAll(/\$([A-Z]{1,10})/g)].map(m => '$' + m[1]);
    const tags = [...html.matchAll(/#([a-zA-Z][a-zA-Z0-9_]*)/g)].map(m => '#' + m[1]);
    const unique = [...new Set([...tickers, ...tags])];
    document.getElementById('j-today-pills').innerHTML = renderPills(unique);
  }

  async function saveEntry(editor) {
    const body = sanitizeEditorHtml(editor.innerHTML);
    const entryDate = new Date().toISOString().slice(0, 10);
    const btn = document.getElementById('j-save-btn');
    btn.textContent = 'Saving…';
    try {
      let saved;
      if (journalTodayId) {
        saved = await put(`/api/journal/entries/${journalTodayId}`, { body, entry_date: entryDate });
      } else {
        saved = await post('/api/journal/entries', { body, entry_date: entryDate });
        journalTodayId = saved.id;
      }
      btn.textContent = 'Saved ✓';
      setTimeout(() => { btn.textContent = 'Save'; }, 2000);
      // Refresh the past entries list
      journalEntries = await get('/api/journal/entries');
      document.getElementById('j-feed').innerHTML =
        renderTodayEditor(saved) + renderPastEntries(journalEntries);
      attachJournalHandlers();
    } catch (_) {
      btn.textContent = 'Error — retry';
      setTimeout(() => { btn.textContent = 'Save'; }, 3000);
    }
  }

  function sanitizeEditorHtml(html) {
    // Strip all tags except permitted set: <b>, <i>, <a>, <span class="neon-token">, <div class="j-embed">
    const tmp = document.createElement('div');
    tmp.innerHTML = html;
    cleanNode(tmp);
    return tmp.innerHTML;
  }

  function cleanNode(node) {
    [...node.childNodes].forEach(child => {
      if (child.nodeType === Node.TEXT_NODE) return;
      if (child.nodeType === Node.ELEMENT_NODE) {
        const tag = child.tagName.toLowerCase();
        const allowed = tag === 'b' || tag === 'i' || tag === 'a'
          || (tag === 'span' && child.classList.contains('neon-token'))
          || (tag === 'div' && child.classList.contains('j-embed'))
          || (tag === 'img' && child.classList.contains('j-embed-thumb'));
        if (!allowed) {
          while (child.firstChild) child.parentNode.insertBefore(child.firstChild, child);
          child.parentNode.removeChild(child);
        } else {
          cleanNode(child);
        }
      }
    });
  }
```

- [ ] **Step 10.3: Verify editor works locally**

```bash
./gradlew bootRun &
# Navigate to Journal page
# Type "$NVDA is up today #macro" — tokens should turn neon in real time
# Paste a YouTube URL — embed card should appear
# Click Save — should POST and return 201
```

- [ ] **Step 10.4: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "feat: journal editor with token detection, paste embeds, and save flow"
```

---

### Task 11: Frontend — Past Entries Feed and Filter Mode

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 11.1: Add `renderPastEntries()` and filter functions**

```javascript
  function renderPastEntries(entries) {
    const today = new Date().toISOString().slice(0, 10);
    const past = entries.filter(e => e.entry_date !== today);
    if (past.length === 0) return '';
    return past.map((e, idx) => renderPastCard(e, idx)).join('');
  }

  function renderPastCard(entry, idx) {
    const ageClass = idx === 0 ? 'age-1' : idx === 1 ? 'age-2' : 'age-old';
    const date = new Date(entry.entry_date + 'T00:00:00');
    const label = date.toLocaleDateString('en-US', { weekday:'short', month:'short', day:'numeric' });
    const tokens = [...entry.tickers.map(t=>'$'+t), ...entry.tags.map(t=>'#'+t)];
    return `
      <div class="j-card ${ageClass}">
        <div class="j-card-head">
          <span class="j-card-date">${label}</span>
        </div>
        <div class="j-editor" style="pointer-events:none;min-height:auto">${colorizeHtml(entry.body)}</div>
        <div class="j-card-foot">
          <div class="j-pills">${renderPills(tokens)}</div>
        </div>
      </div>`;
  }

  function applyJournalFilter(token) {
    journalFilter = token;
    const today = new Date().toISOString().slice(0, 10);
    let filtered, label;

    if (token.startsWith('date:')) {
      const dateStr = token.slice(5);
      filtered = journalEntries.filter(e => e.entry_date === dateStr);
      label = dateStr;
    } else {
      const t = token.startsWith('$') ? token.slice(1) : null;
      const g = token.startsWith('#') ? token.slice(1) : null;
      filtered = journalEntries.filter(e =>
        (t && e.tickers.includes(t)) || (g && e.tags.includes(g)));
      label = token;
    }

    const banner = `
      <div class="j-filter-banner" id="j-filter-banner">
        <span>Viewing: <strong>${label}</strong> (${filtered.length} entries)</span>
        <span class="j-filter-clear" onclick="clearJournalFilter()">×</span>
      </div>`;
    const feed = document.getElementById('j-feed');
    const todayEntry = journalEntries.find(e => e.entry_date === today);
    feed.innerHTML = renderTodayEditor(todayEntry) + banner +
      filtered.filter(e => e.entry_date !== today).map((e, i) => renderPastCard(e, i)).join('');
    attachJournalHandlers();
  }

  function clearJournalFilter() {
    journalFilter = null;
    const feed = document.getElementById('j-feed');
    const today = new Date().toISOString().slice(0, 10);
    const todayEntry = journalEntries.find(e => e.entry_date === today);
    feed.innerHTML = renderTodayEditor(todayEntry) + renderPastEntries(journalEntries);
    attachJournalHandlers();
  }
```

- [ ] **Step 11.2: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "feat: journal past entries feed with progressive dimming and filter mode"
```

---

### Task 12: Frontend — Right Sidebar Widgets

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 12.1: Add stats row, calendar, movers, goals, and most-written render functions**

```javascript
  function renderJStats(stats) {
    // Today P&L is fetched asynchronously after render; placeholder shown first
    return `
      <div class="j-stats-row">
        <div class="j-stat">
          <div class="j-stat-val">${stats.entry_count}</div>
          <div class="j-stat-lbl">Entries</div>
        </div>
        <div class="j-stat">
          <div class="j-stat-val">${stats.current_streak}</div>
          <div class="j-stat-lbl">Streak</div>
        </div>
        <div class="j-stat">
          <div class="j-stat-val" id="j-pnl">—</div>
          <div class="j-stat-lbl">Today</div>
        </div>
      </div>`;
  }

  function renderJCalendar(stats) {
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth();
    const firstDay = new Date(year, month, 1).getDay(); // 0=Sun
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const todayStr = now.toISOString().slice(0, 10);
    const headers = ['Su','Mo','Tu','We','Th','Fr','Sa']
      .map(d => `<div class="j-cal-head">${d}</div>`).join('');
    let cells = '';
    for (let i = 0; i < firstDay; i++) cells += `<div class="j-cal-empty"></div>`;
    for (let d = 1; d <= daysInMonth; d++) {
      const dateStr = `${year}-${String(month+1).padStart(2,'0')}-${String(d).padStart(2,'0')}`;
      const level = stats.calendar[dateStr] ?? 0;
      const cls = level === 2 ? 'j-cal-rich' : level === 1 ? 'j-cal-entry' : 'j-cal-none';
      const todayCls = dateStr === todayStr ? ' j-cal-today' : '';
      const click = level > 0 ? `onclick="applyJournalFilter('date:${dateStr}')"` : '';
      cells += `<div class="j-cal-day ${cls}${todayCls}" title="${dateStr}" ${click}></div>`;
    }
    const monthLabel = now.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
    return `
      <div class="j-widget">
        <div class="j-widget-title">${monthLabel}</div>
        <div class="j-cal-grid">${headers}${cells}</div>
      </div>`;
  }

  function renderJMovers(entries) {
    // Mover data loaded asynchronously after render; replaced by loadJMovers()
    return `
      <div class="j-widget" id="j-movers-widget">
        <div class="j-widget-title">Movers in Your Journal</div>
        <div id="j-movers-inner"><span style="font-size:10px;color:var(--text-dim)">Loading…</span></div>
      </div>`;
  }

  function renderJGoals(goals) {
    const rows = goals.length === 0
      ? '<div style="font-size:10px;color:var(--text-dim)">No goals yet.</div>'
      : goals.map(g => {
          const pct = g.progress_pct != null ? Math.min(g.progress_pct, 100).toFixed(0) : 0;
          const barCls = g.goal_type === 'habit' ? 'habit' : 'milestone';
          const curr = g.current_progress != null ? g.current_progress.toLocaleString() : '—';
          const tgt = g.target_value != null ? g.target_value.toLocaleString() : '∞';
          return `
            <div class="j-goal">
              <div class="j-goal-label">
                <span>${g.label}</span>
                <span style="color:var(--text-dim);font-size:10px">${curr} / ${tgt}</span>
              </div>
              <div class="j-goal-track">
                <div class="j-goal-bar ${barCls}" style="width:${pct}%"></div>
              </div>
            </div>`;
        }).join('');

    return `
      <div class="j-widget" id="j-goals-widget">
        <div class="j-widget-title" style="display:flex;align-items:center">
          Goals
          <button class="j-goals-editbtn" id="j-goals-edit-btn">Edit</button>
        </div>
        <div id="j-goals-list">${rows}</div>
        <div id="j-goals-edit-form" style="display:none">
          ${renderGoalEditForm()}
        </div>
      </div>`;
  }

  function renderGoalEditForm() {
    return `
      <div style="font-size:10px;color:var(--text-dim);margin-bottom:6px">Add new goal:</div>
      <input class="j-goal-input" id="j-new-goal-label" placeholder="Label (e.g. Hit $500K)">
      <select class="j-goal-input" id="j-new-goal-type" style="margin-top:4px">
        <option value="milestone">Milestone</option>
        <option value="habit">Habit</option>
      </select>
      <input class="j-goal-input" id="j-new-goal-target" placeholder="Target value (optional)" type="number" style="margin-top:4px">
      <button class="j-add-goal-btn" onclick="addGoal()">+ Add Goal</button>`;
  }

  function renderJMostWritten(stats) {
    if (!stats.most_mentioned || stats.most_mentioned.length === 0) {
      return `<div class="j-widget">
        <div class="j-widget-title">Most Written About</div>
        <div style="font-size:10px;color:var(--text-dim)">No data yet.</div>
      </div>`;
    }
    const max = stats.most_mentioned[0].count;
    const rows = stats.most_mentioned.slice(0, 8).map(tc => {
      const cls = neonClass(tc.token.replace(/^[$#]/, '').toUpperCase());
      const pct = max > 0 ? (tc.count / max * 100).toFixed(0) : 0;
      return `
        <div class="j-mw-row">
          <span class="j-mw-token neon-token ${cls}" onclick="applyJournalFilter('${tc.token}')">${tc.token}</span>
          <div class="j-mw-bar-wrap"><div class="j-mw-bar" style="width:${pct}%"></div></div>
          <span class="j-mw-count">${tc.count}</span>
        </div>`;
    }).join('');
    return `
      <div class="j-widget">
        <div class="j-widget-title">Most Written About</div>
        ${rows}
      </div>`;
  }
```

- [ ] **Step 12.2: Add async data loaders (P&L + Movers)**

```javascript
  async function loadJournalAsync(entries) {
    // Load portfolio positions and quotes, then update P&L and movers
    try {
      const positions = await get('/api/portfolio/positions');
      if (!positions || positions.length === 0) return;

      const quotes = await Promise.allSettled(
        positions.map(p => get(`/api/quotes/${p.ticker}`)));

      // Compute weighted portfolio day P&L
      let totalValue = 0, weightedPct = 0;
      quotes.forEach((result, i) => {
        if (result.status !== 'fulfilled' || !result.value) return;
        const q = result.value;
        const mv = positions[i].quantity * q.price;
        totalValue += mv;
        weightedPct += mv * (q.change_pct || 0);
      });
      if (totalValue > 0) {
        const pnl = (weightedPct / totalValue);
        const el = document.getElementById('j-pnl');
        if (el) {
          el.textContent = (pnl >= 0 ? '+' : '') + pnl.toFixed(2) + '%';
          el.style.color = pnl >= 0 ? 'var(--green)' : '#ff3864';
        }
      }

      // Movers: positions that user has written about
      const journalTickers = new Set(entries.flatMap(e => e.tickers));
      const movers = quotes
        .map((result, i) => ({ p: positions[i], q: result.value }))
        .filter(x => x.q && journalTickers.has(x.p.ticker))
        .sort((a, b) => Math.abs(b.q.change_pct || 0) - Math.abs(a.q.change_pct || 0))
        .slice(0, 5);

      const inner = document.getElementById('j-movers-inner');
      if (inner) {
        if (movers.length === 0) {
          inner.innerHTML = '<div style="font-size:10px;color:var(--text-dim)">No overlap with portfolio.</div>';
        } else {
          inner.innerHTML = movers.map(({ p, q }) => {
            const pct = q.change_pct || 0;
            const cls = neonClass(p.ticker);
            const dir = pct >= 0 ? 'up' : 'down';
            const entryCount = entries.filter(e => e.tickers.includes(p.ticker)).length;
            return `<div class="j-mover-row">
              <span class="j-mover-ticker neon-token neon-t-${Math.abs(
                p.ticker.split('').reduce((h,c) => (Math.imul(31,h)+c.charCodeAt(0))|0, 0)) % 10}"
                onclick="applyJournalFilter('$${p.ticker}')">${p.ticker}</span>
              <span class="j-mover-pct ${dir}">${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%</span>
              <span class="j-mover-count">${entryCount}</span>
            </div>`;
          }).join('');
        }
      }
    } catch (_) {
      // Movers/P&L are best-effort; silently ignore failures
    }
  }

  async function addGoal() {
    const label = document.getElementById('j-new-goal-label').value.trim();
    const goalType = document.getElementById('j-new-goal-type').value;
    const targetRaw = document.getElementById('j-new-goal-target').value;
    if (!label) return;
    const targetValue = targetRaw ? parseFloat(targetRaw) : null;
    await post('/api/journal/goals', { label, goal_type: goalType, target_value: targetValue });
    await loadJournal(); // Reload full page to reflect updated goals
  }

  function toggleGoalsEdit() {
    const form = document.getElementById('j-goals-edit-form');
    const btn = document.getElementById('j-goals-edit-btn');
    const isVisible = form.style.display !== 'none';
    form.style.display = isVisible ? 'none' : 'block';
    btn.textContent = isVisible ? 'Edit' : 'Done';
  }
```

- [ ] **Step 12.3: Wire `loadJournalAsync` into `loadJournal`**

Inside `loadJournal()`, after the `page.innerHTML = renderJournalLayout(...)` line, add:

```javascript
      // Fire-and-forget: loads P&L and movers after the page is painted
      loadJournalAsync(journalEntries);
```

- [ ] **Step 12.4: Add `put()` and `post()` helpers if not present**

Check if `put()` and `post()` already exist in the JS. If not, add near the existing `get()` helper:

```javascript
  function post(url, body) {
    return fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-API-KEY': KEY },
      body: JSON.stringify(body),
    }).then(r => r.ok ? r.json() : Promise.reject(r));
  }

  function put(url, body) {
    return fetch(url, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'X-API-KEY': KEY },
      body: JSON.stringify(body),
    }).then(r => r.ok ? r.json() : Promise.reject(r));
  }
```

- [ ] **Step 12.5: Full build and test**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 12.6: Smoke test in browser**

```bash
./gradlew bootRun
```

Navigate to `http://localhost:8080`, click Journal. Verify:
- Editor is open and ready
- Typing `$NVDA` turns the token neon-green
- Past entries appear dimmed
- Calendar renders with the current month
- Stats row shows entry count and streak
- Goals widget shows "No goals yet." (or existing goals)
- Most Written About shows top tokens

- [ ] **Step 12.7: Run Spotless and commit**

```bash
./gradlew spotlessApply
git add src/main/resources/static/index.html
git commit -m "feat: journal sidebar widgets (stats, calendar, movers, goals, most-written)"
```

---

## Final Verification

- [ ] **Run full CI-equivalent build:**

```bash
./gradlew spotlessCheck build --no-daemon
```

Expected: BUILD SUCCESSFUL. All unit tests pass. Integration tests pass if Docker is available.

- [ ] **Final commit if anything remains staged:**

```bash
git status  # confirm clean
```
