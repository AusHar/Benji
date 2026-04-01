# Stability Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all 22 issues from the codebase health report — 6 critical frontend bugs, build/CI fragility, backend correctness, test reliability, and accessibility gaps.

**Architecture:** Grouped by file to minimize context switching. Frontend fixes (8 issues) in one task, build.gradle (1), CI workflow (2), backend Java (4 files), tests (3 files), then a11y/SEO polish (3). Each task ends with a verification step.

**Tech Stack:** Java 21, Spring Boot 3.3, Gradle, Single-file HTML/CSS/JS SPA, GitHub Actions

---

### Task 1: Frontend Critical Fixes (index.html)

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html`

All 8 frontend issues are in the single SPA file. Fix them in one pass.

- [ ] **Step 1: Fix `loadJournalAsync` — wrong property access on positions response (line ~3003)**

Change:
```javascript
      const positions = await get('/api/portfolio/positions');
      if (!positions || positions.length === 0) return;

      const quotes = await Promise.allSettled(
        positions.map(p => get(`/api/quotes/${p.ticker}`)));
```

To:
```javascript
      const posData = await get('/api/portfolio/positions');
      const positions = posData?.positions || [];
      if (positions.length === 0) return;

      const quotes = await Promise.allSettled(
        positions.map(p => get(`/api/quotes/${p.ticker}`)));
```

- [ ] **Step 2: Fix `fetchQuote` — canvas destroyed before RAF draws chart (lines ~1935-1958)**

The problem is two `el.innerHTML = html` assignments with a `requestAnimationFrame` between them. Move the news append before the first DOM write.

Change the section from:
```javascript
    el.innerHTML = html;

    if (hRes.status === 'fulfilled' && hRes.value && hRes.value.bars && hRes.value.bars.length > 1) {
      requestAnimationFrame(() => drawChart('priceChart', hRes.value.bars));
    }

    // News
    if (nRes.status === 'fulfilled' && nRes.value?.articles?.length) {
      const articles = nRes.value.articles;
      html += `<div class="section-head" style="margin-top:22px;margin-bottom:10px">
        ...
      </div>
      <div class="news-list">
        ${articles.map(a => `...`).join('')}
      </div>`;
      el.innerHTML = html;
    }
```

To:
```javascript
    // News (append before writing to DOM so canvas isn't destroyed)
    if (nRes.status === 'fulfilled' && nRes.value?.articles?.length) {
      const articles = nRes.value.articles;
      html += `<div class="section-head" style="margin-top:22px;margin-bottom:10px">
        <span class="section-title">Recent News</span>
        <span class="section-hint">${articles.length} article${articles.length !== 1 ? 's' : ''}</span>
      </div>
      <div class="news-list">
        ${articles.map(a => `
          <a class="news-item" href="${a.url}" target="_blank" rel="noopener">
            ${a.image ? `<img class="news-thumb" src="${a.image}" alt="" loading="lazy" onerror="this.style.display='none'">` : ''}
            <div class="news-body">
              <div class="news-headline">${a.headline}</div>
              <div class="news-meta">${a.source || ''}${a.source && a.published_at ? ' · ' : ''}${a.published_at ? ts(a.published_at) : ''}</div>
            </div>
          </a>`).join('')}
      </div>`;
    }

    el.innerHTML = html;

    if (hRes.status === 'fulfilled' && hRes.value && hRes.value.bars && hRes.value.bars.length > 1) {
      requestAnimationFrame(() => drawChart('priceChart', hRes.value.bars));
    }
```

- [ ] **Step 3: Fix `signOutDemo` — clear ticker interval (line ~1234)**

Change:
```javascript
  function signOutDemo() {
    KEY = '';
    sessionStorage.removeItem('KEY');
```

To:
```javascript
  function signOutDemo() {
    clearInterval(_tickerTimer);
    _tickerTimer = null;
    KEY = '';
    sessionStorage.removeItem('KEY');
```

- [ ] **Step 4: Fix `renderJCalendar` — guard against undefined calendar (line ~2905)**

Change:
```javascript
      const level = stats.calendar[dateStr] ?? 0;
```

To:
```javascript
      const level = stats?.calendar?.[dateStr] ?? 0;
```

- [ ] **Step 5: Fix `attachJournalHandlers` call sites — pass module-level state (lines ~2863, ~2870)**

Change both call sites from:
```javascript
    attachJournalHandlers();
```

To:
```javascript
    attachJournalHandlers(journalGoals, journalStats);
```

There are two locations: inside `applyJournalFilter` and inside `clearJournalFilter`.

- [ ] **Step 6: Fix `tryKey` — reject non-OK responses (line ~1262)**

Change:
```javascript
      const r = await fetch('/api/quotes', { headers: { 'X-API-KEY': k } });
      if (r.status === 401) throw 'bad';
      KEY = k;
```

To:
```javascript
      const r = await fetch('/api/quotes', { headers: { 'X-API-KEY': k } });
      if (!r.ok) throw r.status;
      KEY = k;
```

- [ ] **Step 7: Fix eye toggle button touch target (line ~613)**

Change:
```css
    .lock-eye-btn{position:absolute;right:6px;top:50%;transform:translateY(-50%);background:none;border:none;cursor:pointer;padding:2px;color:#888;display:flex;align-items:center}
```

To:
```css
    .lock-eye-btn{position:absolute;right:4px;top:50%;transform:translateY(-50%);background:none;border:none;cursor:pointer;padding:4px;color:#888;display:flex;align-items:center}
```

- [ ] **Step 8: Fix color contrast — demo banner and ticker symbols**

For the demo banner text (line ~621), change `color:#4edd8a99` to `color:#4edd8acc` (80% opacity, raises contrast above 4.5:1):
```css
    .demo-banner{...;color:#4edd8acc;...}
```

For the ticker symbol color (line ~196 in `:root`), change `--text-muted` from `#607a6c` to `#6d8d78` (brighter, meets 4.5:1 against `#030705` at 9px):
```css
      --text-muted:   #6d8d78;
```

- [ ] **Step 9: Add `<main>` landmark and meta description (lines ~6 and ~868)**

In the `<head>` section after the `<title>` tag, add:
```html
  <meta name="description" content="Benji — your personal financial assistant. Track portfolios, analyze trades, and explore live market data.">
```

Change the main content div (the `.main` element around line ~968) from `<div class="main"` to `<main class="main"`, and the matching closing `</div>` to `</main>`.

- [ ] **Step 10: Verify frontend changes**

Open Chrome DevTools, navigate to https://port.adhdquants.com, and verify:
- Journal page loads without errors
- Quotes page shows price chart AND news (not one or the other)
- Demo sign-out doesn't cause console 401 errors
- Eye toggle is clickable and meets 24x24 minimum

---

### Task 2: Build Configuration (build.gradle)

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/build.gradle`

- [ ] **Step 1: Disable plain JAR**

Add after the `plugins` block (after line ~8):
```groovy
jar {
    enabled = false
}
```

This prevents Gradle from producing the `-plain.jar` that caused the deploy crash.

- [ ] **Step 2: Verify only boot JAR is produced**

Run:
```bash
cd apps/api/trader-assistant/trading-dashboard && ./gradlew clean build --no-daemon && ls -la build/libs/
```

Expected: Only `trading-dashboard-0.0.1-SNAPSHOT.jar` (72MB+), no `-plain.jar`.

- [ ] **Step 3: Commit build fix**

```bash
git add apps/api/trader-assistant/trading-dashboard/build.gradle
git commit -m "fix: disable plain jar to prevent deploy of wrong artifact"
```

---

### Task 3: CI/CD Hardening (ci.yml)

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Add rollback on failed health check and improve health polling**

Replace the deploy job's `Deploy JAR to Lightsail`, `Restart service`, and `Verify health` steps (lines 61-73) with:

```yaml
      - name: Deploy JAR to Lightsail
        run: |
          JAR=$(find dist -name "*.jar" ! -name "*-plain.jar" | head -1)
          ssh -i ~/.ssh/deploy.pem ubuntu@107.22.236.28 \
            "cp /opt/benji/benji.jar /opt/benji/benji.jar.prev 2>/dev/null || true"
          rsync -az -e "ssh -i ~/.ssh/deploy.pem" "$JAR" ubuntu@107.22.236.28:/opt/benji/benji.jar

      - name: Restart service
        run: |
          ssh -i ~/.ssh/deploy.pem ubuntu@107.22.236.28 "sudo systemctl restart benji"

      - name: Verify health
        run: |
          for i in $(seq 1 20); do
            if curl --fail --silent https://port.adhdquants.com/actuator/health; then
              echo ""
              echo "Health check passed on attempt $i"
              exit 0
            fi
            echo "Attempt $i failed, waiting 15s..."
            sleep 15
          done
          echo "Health check failed after 5 minutes — rolling back"
          ssh -i ~/.ssh/deploy.pem ubuntu@107.22.236.28 \
            "cp /opt/benji/benji.jar.prev /opt/benji/benji.jar && sudo systemctl restart benji"
          exit 1
```

This adds:
- Backup of previous JAR before deploy
- Polling loop (20 attempts x 15s = 5 min max) instead of fixed sleep
- Automatic rollback to previous JAR on health check failure

- [ ] **Step 2: Commit CI changes**

```bash
git add .github/workflows/ci.yml
git commit -m "fix: add deploy rollback and improve health check polling"
```

---

### Task 4: Backend Java Fixes

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/controllers/DemoController.java`
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/persistence/JournalEntryRepository.java`
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/service/DefaultJournalService.java`
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/java/com/austinharlan/trading_dashboard/service/DefaultPortfolioService.java`

- [ ] **Step 1: Add rate limiting to demo endpoint**

In `DemoController.java`, add a simple in-memory cooldown. Replace the class with:

```java
package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.service.DemoService;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

  private static final long COOLDOWN_MS = 5_000;
  private final AtomicLong lastReset = new AtomicLong(0);

  private final DemoService demoService;

  public DemoController(DemoService demoService) {
    this.demoService = demoService;
  }

  @PostMapping("/api/demo/session")
  public ResponseEntity<Map<String, String>> startDemoSession() {
    long now = System.currentTimeMillis();
    long prev = lastReset.get();
    if (now - prev < COOLDOWN_MS || !lastReset.compareAndSet(prev, now)) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body(Map.of("error", "Please wait a few seconds before retrying"));
    }
    demoService.resetDemoData();
    return ResponseEntity.ok(Map.of("apiKey", "demo"));
  }
}
```

This uses `AtomicLong` + `compareAndSet` to ensure only one reset per 5 seconds, with no external dependencies.

- [ ] **Step 2: Fix non-portable JPQL in JournalEntryRepository**

In `JournalEntryRepository.java`, replace the YEAR/MONTH query (lines ~38-41) with a date range query:

```java
  @Query(
      "SELECT COUNT(DISTINCT e.entryDate) FROM JournalEntryEntity e "
          + "WHERE e.userId = :userId AND e.entryDate >= :startDate AND e.entryDate < :endDate")
  long countDistinctEntryDatesInMonthByUserId(
      @Param("userId") Long userId,
      @Param("startDate") java.time.LocalDate startDate,
      @Param("endDate") java.time.LocalDate endDate);
```

Then update the caller in `DefaultJournalService.java`. Find where `countDistinctEntryDatesInMonthByUserId` is called and change the arguments from `(userId, year, month)` to date range parameters:

```java
    LocalDate startDate = YearMonth.of(year, month).atDay(1);
    LocalDate endDate = startDate.plusMonths(1);
    long daysThisMonth =
        journalEntryRepository.countDistinctEntryDatesInMonthByUserId(userId, startDate, endDate);
```

Add `import java.time.YearMonth;` and `import java.time.LocalDate;` if not already present.

- [ ] **Step 3: Fix duplicate setQty/setBasis in DefaultPortfolioService.addHolding**

In `DefaultPortfolioService.java` lines ~56-74, replace with a clean upsert:

```java
  @Override
  @Transactional
  public PortfolioHolding addHolding(String ticker, BigDecimal quantity, BigDecimal pricePerShare) {
    long userId = UserContext.current().userId();
    BigDecimal totalBasis = quantity.multiply(pricePerShare);
    PortfolioPositionEntity entity =
        repository
            .findByUserIdAndTicker(userId, ticker)
            .orElseGet(() -> new PortfolioPositionEntity(userId, ticker, quantity, totalBasis));
    entity.setQty(quantity);
    entity.setBasis(totalBasis);
    return toHolding(repository.save(entity));
  }
```

This removes the `.map()` lambda entirely. The `setQty`/`setBasis` after `orElseGet` handles both cases uniformly.

- [ ] **Step 4: Run spotless and tests**

```bash
cd apps/api/trader-assistant/trading-dashboard
./gradlew spotlessApply
./gradlew test --no-daemon
```

Expected: All tests pass.

- [ ] **Step 5: Commit backend fixes**

```bash
git add -A apps/api/trader-assistant/trading-dashboard/src/main/java/
git commit -m "fix: demo rate limit, portable JPQL, clean up portfolio upsert"
```

---

### Task 5: Test Reliability Fixes

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/test/java/com/austinharlan/trading_dashboard/MultiTenancyIT.java`
- Modify: `apps/api/trader-assistant/trading-dashboard/src/test/java/com/austinharlan/trading_dashboard/TradeIT.java`
- Modify: `apps/api/trader-assistant/trading-dashboard/src/test/java/com/austinharlan/trading_dashboard/DemoIT.java`

- [ ] **Step 1: Fix MultiTenancyIT — also delete user rows in cleanup**

In `MultiTenancyIT.java`, change the cleanup method (lines ~51-66) to also delete the user entities:

```java
  @org.junit.jupiter.api.AfterEach
  void cleanup() {
    transactionTemplate.executeWithoutResult(
        status -> {
          if (userAKey != null) {
            userRepository
                .findByApiKey(userAKey)
                .ifPresent(
                    u -> {
                      portfolioRepository.deleteAllByUserId(u.getId());
                      userRepository.delete(u);
                    });
          }
          if (userBKey != null) {
            userRepository
                .findByApiKey(userBKey)
                .ifPresent(
                    u -> {
                      portfolioRepository.deleteAllByUserId(u.getId());
                      userRepository.delete(u);
                    });
          }
        });
  }
```

- [ ] **Step 2: Fix TradeIT — scope deletion to test user**

In `TradeIT.java`, find the user ID used for test setup. Change the cleanup (line ~43) from:

```java
    tradeRepository.deleteAll();
```

To use scoped deletion. First check how the test user ID is obtained — look for `UserContext` setup or a test API key. The cleanup should delete only that user's trades:

```java
    tradeRepository.deleteAllByUserId(testUserId);
```

Where `testUserId` is the field already used by the test class for authenticated requests.

- [ ] **Step 3: Fix DemoIT — remove duplicate test**

In `DemoIT.java`, delete the `postDemoSession_requiresNoAuth` test method (lines ~49-53) since it is identical to `postDemoSession_returns200WithApiKey`.

- [ ] **Step 4: Run tests to verify**

```bash
cd apps/api/trader-assistant/trading-dashboard
./gradlew spotlessApply
./gradlew test --no-daemon
```

Expected: All tests pass.

- [ ] **Step 5: Commit test fixes**

```bash
git add -A apps/api/trader-assistant/trading-dashboard/src/test/
git commit -m "fix: scope test cleanup, delete leaked user rows, remove duplicate test"
```

---

### Task 6: Final Verification and Deploy

- [ ] **Step 1: Run full build**

```bash
cd apps/api/trader-assistant/trading-dashboard
./gradlew spotlessCheck build --no-daemon
```

Expected: BUILD SUCCESSFUL, all tests pass, no spotless violations.

- [ ] **Step 2: Verify only one JAR produced**

```bash
ls -la build/libs/
```

Expected: Only `trading-dashboard-0.0.1-SNAPSHOT.jar`, no `-plain.jar`.

- [ ] **Step 3: Create PR, merge, and verify deploy**

Push, create PR, wait for CI green, merge to main, verify health check at https://port.adhdquants.com/actuator/health.

- [ ] **Step 4: Run Lighthouse audit on deployed site**

Verify:
- Accessibility score >= 95
- No color contrast failures
- No touch target failures
- `<main>` landmark present
- Meta description present
