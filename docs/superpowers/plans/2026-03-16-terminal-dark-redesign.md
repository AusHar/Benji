# Terminal Dark Redesign — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite the Benji trading dashboard frontend with the terminal dark theme, including the new interactive watchlist tile system.

**Architecture:** All changes are confined to the single-file SPA at `src/main/resources/static/index.html`. Existing JavaScript business logic (auth, routing, API calls, `fitQuoteText`) is preserved and updated to work with the new HTML structure. New watchlist tile system is the only net-new JS component.

**Tech Stack:** Vanilla HTML/CSS/JS, IBM Plex Mono + Fraunces (Google Fonts), SVG sparklines, Spring Boot static resource serving.

**Reference:** Spec at `docs/superpowers/specs/2026-03-16-terminal-dark-redesign-design.md`, mockup at `.superpowers/brainstorm/2598-1773700949/dashboard-mockup-v8.html`.

**Run all commands from:** `apps/api/trader-assistant/trading-dashboard/`

---

## Chunk 1: Design Foundation

### Task 1: Replace design tokens, fonts, and global animations

**Files:**
- Modify: `src/main/resources/static/index.html` (CSS `<style>` block — `:root`, `html/body`, font import, animations, scrollbar)

The existing `index.html` uses Inter + a forest-green light theme. This task replaces only the `:root` variables, font import, base `html/body` styles, and adds the three new animations. Everything else stays untouched.

- [ ] **Step 1: Replace the Google Fonts import**

In `<head>`, replace:
```html
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&display=swap" rel="stylesheet">
```
With:
```html
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@300;400;500&family=Fraunces:ital,opsz,wght@1,9..144,300&display=swap" rel="stylesheet">
```

- [ ] **Step 2: Replace `:root` CSS variables**

Replace the entire `:root { ... }` block with:
```css
:root {
  --bg:           #050a07;
  --bg-mid:       #09100b;
  --bg-card:      #111a13;
  --bg-card-hi:   #172019;
  --border:       rgba(78,221,138,.11);
  --border-mid:   rgba(78,221,138,.22);
  --border-hi:    rgba(78,221,138,.38);
  --green:        #4edd8a;
  --green-dim:    rgba(78,221,138,.65);
  --green-faint:  rgba(78,221,138,.07);
  --glow-sm:      0 0 8px rgba(78,221,138,.18), 0 0 2px rgba(78,221,138,.25);
  --glow-ring:    0 0 0 1px rgba(78,221,138,.3), 0 0 14px rgba(78,221,138,.2), 0 0 30px rgba(78,221,138,.08);
  --glow-ring-hi: 0 0 0 1px rgba(78,221,138,.45), 0 0 20px rgba(78,221,138,.28), 0 0 44px rgba(78,221,138,.12);
  --red:          #f07868;
  --amber:        #e8b45a;
  --text:         #f2faf4;
  --text-mid:     #c0d8c8;
  --text-dim:     #85aa92;
  --text-muted:   #607a6c;
  --mono:         'IBM Plex Mono', monospace;
  --serif:        'Fraunces', Georgia, serif;
  --sidebar-w:    192px;
  --sidebar-col:  54px;
}
```

- [ ] **Step 3: Replace `html, body` base styles**

Replace the existing `html, body { ... }` block with:
```css
html, body {
  height: 100%;
  background: var(--bg);
  color: var(--text);
  font-family: var(--mono);
  font-size: 13px;
  -webkit-font-smoothing: antialiased;
  overflow: hidden;
}
```

- [ ] **Step 4: Add animations and scrollbar after the `html, body` block**

```css
@keyframes glowpulse {
  0%,100% { box-shadow: var(--glow-ring-hi); }
  50%      { box-shadow: 0 0 0 1px rgba(78,221,138,.5), 0 0 24px rgba(78,221,138,.3), 0 0 50px rgba(78,221,138,.12); }
}
@keyframes hum {
  0%,100% { box-shadow: 0 0 4px var(--green), 0 0 8px rgba(78,221,138,.4); }
  50%      { box-shadow: 0 0 8px var(--green), 0 0 18px rgba(78,221,138,.6), 0 0 28px rgba(78,221,138,.2); }
}
@keyframes tick { 0% { transform: translateX(0); } 100% { transform: translateX(-50%); } }
::-webkit-scrollbar { width: 4px; }
::-webkit-scrollbar-thumb { background: rgba(78,221,138,.18); border-radius: 2px; }
```

- [ ] **Step 5: Build to verify no regressions**

```bash
./gradlew build
```
Expected: `BUILD SUCCESSFUL`. The page will look broken visually (old HTML with new variables) — that is expected at this stage.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "redesign: replace design tokens, fonts, and global animations"
```

---

## Chunk 2: Global Shell

### Task 2: Sidebar — HTML, CSS, collapse behavior

**Files:**
- Modify: `src/main/resources/static/index.html` (sidebar HTML in `<body>`, sidebar CSS rules, sidebar JS)

- [ ] **Step 1: Replace sidebar CSS**

Remove all existing sidebar CSS (`.sidebar`, `.sidebar-brand`, `.brand`, `.brand-icon`, `.brand-name`, `.brand-sub`, `.sidebar-nav`, `.nav-group-label`, `.nav-item`, `.sidebar-footer`, `.key-badge`, `.key-dot`, `.key-label`) and replace with:
```css
.sidebar {
  width: var(--sidebar-w);
  background: var(--bg-mid);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width .3s cubic-bezier(.4,0,.2,1);
  overflow: hidden;
  z-index: 10;
}
.sidebar.collapsed { width: var(--sidebar-col); }
.sidebar-brand {
  height: 78px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 16px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
  white-space: nowrap;
  overflow: hidden;
}
.brand-mark {
  font-family: var(--serif);
  font-style: italic;
  font-size: 21px;
  font-weight: 300;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 10px;
  font-optical-sizing: auto;
}
.brand-mark .icon { font-size: 18px; flex-shrink: 0; }
.brand-mark .name { transition: opacity .2s; }
.sidebar.collapsed .brand-mark .name { opacity: 0; }
.brand-sub {
  font-size: 8px;
  color: var(--text-muted);
  letter-spacing: 1px;
  text-transform: uppercase;
  margin-top: 3px;
  padding-left: 28px;
  white-space: nowrap;
  transition: opacity .2s;
}
.sidebar.collapsed .brand-sub { opacity: 0; }
.sidebar-nav {
  flex: 1;
  padding: 10px 8px;
  display: flex;
  flex-direction: column;
  gap: 1px;
  overflow: hidden;
}
.nav-group {
  font-size: 7.5px;
  font-weight: 500;
  letter-spacing: 1.2px;
  text-transform: uppercase;
  color: var(--text-muted);
  padding: 10px 8px 4px;
  white-space: nowrap;
  transition: opacity .15s;
}
.sidebar.collapsed .nav-group { opacity: 0; }
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 6px;
  color: var(--text-dim);
  cursor: pointer;
  transition: background .15s, color .15s, box-shadow .2s;
  white-space: nowrap;
  overflow: hidden;
  border: 1px solid transparent;
}
.nav-item:hover { background: rgba(78,221,138,.06); color: var(--text-mid); border-color: rgba(78,221,138,.12); box-shadow: var(--glow-sm); }
.nav-item.active { background: rgba(78,221,138,.1); color: var(--green); border-color: rgba(78,221,138,.2); box-shadow: var(--glow-sm); }
.nav-item svg { flex-shrink: 0; opacity: .8; }
.nav-item.active svg { opacity: 1; }
.nav-label { font-size: 11px; transition: opacity .15s; }
.sidebar.collapsed .nav-label { opacity: 0; }
.sidebar-footer { padding: 12px 8px; border-top: 1px solid var(--border); flex-shrink: 0; }
.conn-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  background: var(--green-faint);
  border: 1px solid rgba(78,221,138,.14);
  white-space: nowrap;
  box-shadow: var(--glow-sm);
}
.conn-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--green);
  flex-shrink: 0;
  animation: hum 2.5s ease-in-out infinite;
}
.conn-label { font-size: 10px; color: var(--green-dim); transition: opacity .15s; }
.sidebar.collapsed .conn-label { opacity: 0; }
```

- [ ] **Step 2: Replace sidebar HTML**

Replace the existing `<aside class="sidebar">` block with:
```html
<aside class="sidebar" id="sidebar">
  <div class="sidebar-brand">
    <div class="brand-mark">
      <span class="icon">🌲</span>
      <span class="name">Benji</span>
    </div>
    <div class="brand-sub">Financial assistant</div>
  </div>
  <nav class="sidebar-nav">
    <div class="nav-group">Overview</div>
    <div class="nav-item active" data-page="dashboard">
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.4">
        <rect x="1" y="1" width="5" height="5" rx="1"/><rect x="8" y="1" width="5" height="5" rx="1"/>
        <rect x="1" y="8" width="5" height="5" rx="1"/><rect x="8" y="8" width="5" height="5" rx="1"/>
      </svg>
      <span class="nav-label">Dashboard</span>
    </div>
    <div class="nav-group">Markets</div>
    <div class="nav-item" data-page="quotes">
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.4">
        <polyline points="1,10 4,6 7,8 13,2"/><polyline points="9,2 13,2 13,6"/>
      </svg>
      <span class="nav-label">Quotes</span>
    </div>
    <div class="nav-item" data-page="portfolio">
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.4">
        <rect x="1" y="4" width="12" height="9" rx="1.5"/><path d="M5 4V3a2 2 0 0 1 4 0v1"/>
      </svg>
      <span class="nav-label">Portfolio</span>
    </div>
    <div class="nav-group">Personal</div>
    <div class="nav-item" data-page="finance">
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.4">
        <rect x="1" y="2" width="12" height="10" rx="1.5"/><path d="M1 5.5h12M5 2v3.5"/>
      </svg>
      <span class="nav-label">Finance</span>
    </div>
  </nav>
  <div class="sidebar-footer">
    <div class="conn-badge" id="keyBadge" title="Click to sign out">
      <div class="conn-dot"></div>
      <span class="conn-label">Connected</span>
    </div>
  </div>
</aside>
```

- [ ] **Step 3: Add sidebar collapse JS**

Near the bottom of `<script>`, after the existing event listeners, add:
```javascript
/* ── Sidebar collapse ───────────────────── */
const _sb = document.getElementById('sidebar');
let _sbTimer = null;
function _sbStartCollapse() {
  clearTimeout(_sbTimer);
  _sbTimer = setTimeout(() => _sb.classList.add('collapsed'), 4000);
}
_sb.addEventListener('mouseenter', () => { clearTimeout(_sbTimer); _sb.classList.remove('collapsed'); });
_sb.addEventListener('mouseleave', _sbStartCollapse);
_sbStartCollapse(); // start timer immediately on page load
```

- [ ] **Step 4: Build and verify**

```bash
./gradlew build
```
Expected: `BUILD SUCCESSFUL`.

Then run `./gradlew bootRun` and navigate to `http://localhost:8080`. Sign in. Verify:
- Sidebar collapses to icons after 4 seconds automatically
- Hover over sidebar: it expands
- Mouse off: timer restarts
- Brand border aligns with topbar (check after Task 3 completes)

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "redesign: sidebar with collapse behavior"
```

---

### Task 3: Ticker bar, topbar, and main shell

**Files:**
- Modify: `src/main/resources/static/index.html` (ticker + topbar HTML/CSS, `.main`, `.content`)

- [ ] **Step 1: Replace main shell + ticker bar + topbar CSS**

Remove existing `.main`, `.ticker-bar`, `.ticker-track`, `.ticker-item`, `.topbar`, `.topbar-title`, `.topbar-date`, `.content` rules and replace with:
```css
.layout { display: flex; height: 100vh; }
.main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.ticker-bar {
  height: 28px;
  background: #030705;
  border-bottom: 1px solid var(--border);
  overflow: hidden;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.ticker-track { display: flex; white-space: nowrap; animation: tick 28s linear infinite; }
.ticker-bar:hover .ticker-track { animation-play-state: paused; }
.ticker-item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 20px;
  font-size: 10px;
  border-right: 1px solid var(--border);
}
.t-sym { color: var(--text-muted); font-size: 9px; letter-spacing: .5px; }
.t-price { color: var(--text-mid); }
.t-chg.pos { color: var(--green); }
.t-chg.neg { color: var(--red); }
.t-chg.neu { color: var(--text-dim); }
.topbar {
  height: 50px;
  padding: 0 28px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  background: var(--bg-mid);
}
.topbar-title {
  font-family: var(--serif);
  font-style: italic;
  font-size: 21px;
  font-weight: 300;
  color: var(--text);
  font-optical-sizing: auto;
}
.topbar-meta { display: flex; align-items: center; gap: 14px; }
.topbar-date { font-size: 11px; color: var(--text-mid); }
.live-pill {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 9px;
  letter-spacing: .8px;
  color: var(--green-dim);
  background: var(--green-faint);
  border: 1px solid rgba(78,221,138,.18);
  padding: 3px 9px;
  border-radius: 20px;
  box-shadow: var(--glow-sm);
}
.live-dot {
  width: 5px; height: 5px;
  border-radius: 50%;
  background: var(--green);
  animation: hum 2s ease-in-out infinite;
}
.content { flex: 1; overflow-y: auto; padding: 24px 28px; }
```

- [ ] **Step 2: Replace ticker bar and topbar HTML**

Replace the `<div class="ticker-bar" ...>` and `<div class="topbar">` blocks with:
```html
<div class="ticker-bar" id="tickerBar" style="display:none">
  <div class="ticker-track" id="tickerTrack"></div>
</div>
<div class="topbar">
  <div class="topbar-title" id="pageTitle">Dashboard</div>
  <div class="topbar-meta">
    <div class="topbar-date" id="pageDate"></div>
    <div class="live-pill" id="livePill" style="display:none">
      <div class="live-dot"></div>LIVE
    </div>
  </div>
</div>
```

- [ ] **Step 3: Show LIVE pill after auth**

In the `launch()` function, add after `startTicker()`:
```javascript
document.getElementById('livePill').style.display = 'flex';
```

- [ ] **Step 4: Update `buildTickerItems()` to use new markup**

Find the existing `buildTickerItems(quotes)` function. Update it to produce new `ticker-item` markup:
```javascript
function buildTickerItems(quotes) {
  return quotes.map(q => {
    const chg = q.change_pct;
    const cls = chg == null ? 'neu' : chg > 0 ? 'pos' : chg < 0 ? 'neg' : 'neu';
    const chgStr = chg == null ? '—' : (chg >= 0 ? '+' : '') + chg.toFixed(2) + '%';
    return `<div class="ticker-item">
      <span class="t-sym">${q.symbol}</span>
      <span class="t-price">${fmtTickerPrice(q.price)}</span>
      <span class="t-chg ${cls}">${chgStr}</span>
    </div>`;
  }).join('') ;
}
```

- [ ] **Step 5: Build and verify**

```bash
./gradlew build
```
Expected: `BUILD SUCCESSFUL`.

Run `./gradlew bootRun`, sign in. Verify:
- Ticker bar scrolls with the new styling
- Topbar shows page title in Fraunces italic + date + LIVE pill
- Sidebar brand bottom border aligns flush with the topbar bottom border

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "redesign: ticker bar, topbar, and main shell"
```

---

## Chunk 3: Dashboard Stat Cards

### Task 4: Stat cards with trend badges

**Files:**
- Modify: `src/main/resources/static/index.html` (stat card CSS, `statCard()` JS helper, `loadDashboard()`)

- [ ] **Step 1: Replace stat card CSS**

Remove all existing `.stat-grid`, `.card`, `.card-label`, `.card-value`, `.card-hint` rules and replace with:
```css
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}
.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 14px 16px;
  transition: border-color .18s, box-shadow .22s, background .18s;
  box-shadow: inset 0 1px 0 rgba(78,221,138,.06);
}
.stat-card:hover { border-color: var(--border-mid); background: var(--bg-card-hi); box-shadow: var(--glow-ring); }
.stat-label { font-size: 9px; font-weight: 500; letter-spacing: .9px; text-transform: uppercase; color: var(--text-mid); margin-bottom: 8px; }
.stat-value { font-size: 22px; font-weight: 300; letter-spacing: -.5px; font-variant-numeric: tabular-nums; line-height: 1; color: var(--text); }
.stat-value.amber { color: var(--amber); }
.stat-value.green { color: var(--green); text-shadow: 0 0 20px rgba(78,221,138,.35); }
.stat-value.red   { color: var(--red); }
.stat-hint { font-size: 9.5px; color: var(--text-dim); margin-top: 5px; display: flex; align-items: center; gap: 5px; }
.stat-trend { font-size: 9px; font-weight: 500; display: inline-flex; align-items: center; gap: 2px; padding: 1px 5px; border-radius: 3px; }
.stat-trend.up   { color: var(--green); background: rgba(78,221,138,.1); }
.stat-trend.down { color: var(--red);   background: rgba(240,120,104,.1); }
```

- [ ] **Step 2: Replace `statCard()` helper**

Replace the existing `statCard` function with one that supports an optional trend badge:
```javascript
// statCard(label, value, valueCls, hint, trendPct)
// trendPct: number|null — positive = up, negative = down, null = no badge
const statCard = (label, value, valueCls, hint, trendPct) => {
  let trendHtml = '';
  if (trendPct != null) {
    const cls  = trendPct >= 0 ? 'up' : 'down';
    const sign = trendPct >= 0 ? '▲' : '▼';
    const abs  = Math.abs(trendPct).toFixed(2);
    trendHtml  = `<span class="stat-trend ${cls}">${sign} ${abs}%</span>`;
  }
  return `<div class="stat-card">
    <div class="stat-label">${label}</div>
    <div class="stat-value ${valueCls}">${value}</div>
    ${hint || trendHtml ? `<div class="stat-hint">${trendHtml}${hint ? `<span>${hint}</span>` : ''}</div>` : ''}
  </div>`;
};
```

- [ ] **Step 3: Update dashboard HTML container**

In the dashboard page HTML, update the stat grid container from `class="stat-grid"` to `class="stat-row"`:
```html
<div class="stat-row" id="dash-stats">
  <div class="state-box"><span class="spinner"></span></div>
</div>
```

- [ ] **Step 4: Update `loadDashboard()` to pass trend data**

In `loadDashboard()`, the existing `cards.push(statCard(...))` calls don't pass trend data. Update the portfolio and finance card calls. Since the current API doesn't return daily P&L deltas directly, pass `null` for trend on existing cards for now — the stat card renders cleanly without a badge when `trendPct` is `null`.

```javascript
// Example of updated statCard calls:
cards.push(statCard('Positions',  p.positions_count,             '',      '', null));
cards.push(statCard('Cost Basis', money(p.total_cost_basis),     'amber', p.as_of ? 'As of ' + dt(p.as_of) : '', null));
// Finance cards similarly
cards.push(statCard('MTD Spend',  money(f.month_to_date_spend),  'amber', 'Month to date', null));
```

Also update the `loadPortfolio()` function's existing `statCard()` calls in the same way (add `null` as 5th argument).

- [ ] **Step 5: Build and verify**

```bash
./gradlew build
```
Expected: `BUILD SUCCESSFUL`.

Run `./gradlew bootRun`. Navigate to Dashboard. Verify:
- Stat cards render in a 4-column grid with terminal dark styling
- Hover state shows green glow ring
- No JavaScript errors in browser console

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "redesign: dashboard stat cards with trend badge support"
```

---

## Chunk 4: Watchlist Tiles — At Rest

### Task 5: Watchlist tile grid CSS + at-rest tile rendering

**Files:**
- Modify: `src/main/resources/static/index.html` (watchlist CSS, `renderWatchlistTiles()`, new helper functions)

- [ ] **Step 1: Add watchlist section header CSS**

Replace existing `.section-head`, `.section-title`, `.section-hint`, `.btn` (relevant parts) with:
```css
.section-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 12px; }
.section-title { font-size: 9px; font-weight: 500; letter-spacing: 1.2px; text-transform: uppercase; color: var(--text-dim); }
.section-action {
  font-size: 9.5px; color: var(--green-dim); background: none;
  border: 1px solid transparent; cursor: pointer; font-family: var(--mono);
  padding: 2px 8px; border-radius: 4px;
  transition: color .15s, border-color .15s, box-shadow .2s;
}
.section-action:hover { color: var(--green); border-color: rgba(78,221,138,.2); box-shadow: var(--glow-sm); }
```

- [ ] **Step 2: Add watchlist grid and tile CSS**

```css
/* ── Watchlist grid ─────────────────────── */
.watchlist-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  grid-auto-flow: dense;
  gap: 10px;
  align-items: start;
}
.wl-tile {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px 14px 10px;
  cursor: pointer;
  transition: border-color .18s, background .18s, box-shadow .22s;
  position: relative;
  overflow: hidden;
  box-shadow: inset 0 1px 0 rgba(78,221,138,.05);
}
.wl-tile::before {
  content: '';
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 3px;
  background: var(--strip-color, transparent);
}
.pos-tile { --strip-color: rgba(78,221,138,.5); }
.neg-tile { --strip-color: rgba(240,120,104,.5); }
.neu-tile { --strip-color: rgba(200,200,200,.15); }
.wl-tile:hover { background: var(--bg-card-hi); border-color: var(--border-mid); box-shadow: var(--glow-ring); }
.wl-tile.expanded {
  grid-column: span 2;
  background: var(--bg-card-hi);
  border-color: var(--border-hi);
  animation: glowpulse 3s ease-in-out infinite;
}
/* Tile header row */
.tile-top { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 4px; }
.tile-sym { font-size: 10.5px; font-weight: 500; color: var(--text-mid); letter-spacing: .5px; }
.tile-chg { font-size: 8.5px; font-weight: 500; padding: 2px 6px; border-radius: 3px; }
.tile-chg.pos { color: var(--green); background: rgba(78,221,138,.1); }
.tile-chg.neg { color: var(--red);   background: rgba(240,120,104,.1); }
.tile-chg.neu { color: var(--text-dim); background: rgba(255,255,255,.05); }
/* Tile price — sentiment-colored */
.tile-price { font-size: 18px; font-weight: 300; letter-spacing: -.4px; font-variant-numeric: tabular-nums; margin-bottom: 8px; line-height: 1.1; }
.pos-tile .tile-price { color: var(--amber); }
.neg-tile .tile-price { color: rgba(240,120,104,.85); }
.neu-tile .tile-price { color: rgba(232,180,90,.55); }
/* Sparkline */
.tile-spark { display: block; width: 100%; height: 36px; }
.wl-tile.expanded .tile-spark { display: none; }
/* Compact news */
.tile-news-compact { margin-top: 8px; padding-top: 8px; border-top: 1px solid var(--border); display: flex; flex-direction: column; gap: 5px; }
.tnc-item { display: flex; gap: 6px; align-items: baseline; }
.tnc-dot { width: 3px; height: 3px; border-radius: 50%; background: var(--text-muted); flex-shrink: 0; margin-top: 4px; }
.tnc-headline { font-size: 9px; color: var(--text-dim); line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; transition: color .15s; }
.wl-tile:hover .tnc-headline { color: var(--text-mid); }
.wl-tile.expanded .tile-news-compact { display: none; }
```

- [ ] **Step 3: Add `parsePct()` and `sparklinePlaceholder()` helpers before `renderWatchlistTiles()`**

```javascript
/* ── Watchlist helpers ───────────────────── */
function parsePct(str) {
  if (str == null) return 0;
  return parseFloat(String(str).replace('−', '-').replace('+', ''));
}

function tileClass(chgPct) {
  if (chgPct == null) return 'neu-tile';
  if (chgPct > 0)  return 'pos-tile';
  if (chgPct < 0)  return 'neg-tile';
  return 'neu-tile';
}

function fmtChg(chgPct) {
  if (chgPct == null) return { text: '—', cls: 'neu' };
  const sign = chgPct >= 0 ? '+' : '';
  const cls  = chgPct > 0 ? 'pos' : chgPct < 0 ? 'neg' : 'neu';
  return { text: sign + chgPct.toFixed(2) + '%', cls };
}

// Returns SVG string for a placeholder sparkline (no chart data yet)
function sparklinePlaceholderSVG() {
  return `<svg class="tile-spark" viewBox="0 0 120 36" preserveAspectRatio="none">
    <line x1="0" y1="18" x2="120" y2="18"
      stroke="rgba(255,255,255,.12)" stroke-width="1" stroke-dasharray="4,5"/>
  </svg>`;
}

// Returns compact news HTML if >= 2 headlines available, else empty string
function compactNewsHTML(headlines) {
  if (!headlines || headlines.length < 2) return '';
  return `<div class="tile-news-compact">
    ${headlines.slice(0, 2).map(h => `
      <div class="tnc-item">
        <div class="tnc-dot"></div>
        <div class="tnc-headline">${h}</div>
      </div>`).join('')}
  </div>`;
}
```

- [ ] **Step 4: Rewrite `renderWatchlistTiles()`**

Replace the existing function entirely:
```javascript
function renderWatchlistTiles(list, quotes) {
  const priceMap = {};
  quotes.forEach(q => { if (q) priceMap[q.symbol] = q; });

  // Build tile data, then sort by change_pct descending (winners first)
  const tiles = list.map(sym => {
    const q = priceMap[sym];
    return { sym, q, chgPct: q ? (q.change_pct ?? null) : null };
  });
  tiles.sort((a, b) => parsePct(b.chgPct) - parsePct(a.chgPct));

  const html = tiles.map(({ sym, q, chgPct }) => {
    const cls  = tileClass(chgPct);
    const chg  = fmtChg(chgPct);
    const price = q ? money(q.price) : '—';
    return `<div class="wl-tile ${cls}" id="wl-${sym}" onclick="toggleTile('${sym}',event)">
      <button class="exp-close" onclick="toggleTile('${sym}',event)" style="display:none">✕</button>
      <div class="tile-top">
        <div class="tile-sym">${sym}</div>
        <div class="tile-chg ${chg.cls}">${chg.text}</div>
      </div>
      <div class="tile-price">${q ? price : '<span class="spinner"></span>'}</div>
      ${sparklinePlaceholderSVG()}
      ${compactNewsHTML([])}
      <div class="tile-expanded-body" style="display:none"></div>
    </div>`;
  });

  // Add-ticker input tile
  html.push(`<div class="wl-add-tile" id="wl-add-tile" style="display:none">
    <input id="watchlistAddInput" type="text" placeholder="Add ticker…" maxlength="12"
           onkeydown="if(event.key==='Enter')addToWatchlist()">
    <button onclick="addToWatchlist()">+</button>
  </div>`);

  const grid = document.getElementById('dash-watchlist');
  grid.innerHTML = html.join('');
  grid.classList.toggle('watchlist-editing', watchlistEditing);
}
```

- [ ] **Step 5: Update the dashboard HTML container**

Replace `<div class="quote-grid" id="dash-watchlist">` with:
```html
<div class="watchlist-grid" id="dash-watchlist"></div>
```

Update the Edit button:
```html
<button class="section-action" id="editWatchlistBtn" onclick="toggleWatchlistEdit()">Edit</button>
```

- [ ] **Step 6: Build and verify**

```bash
./gradlew build
```
Expected: `BUILD SUCCESSFUL`.

Run `./gradlew bootRun`. Navigate to Dashboard. Verify:
- Watchlist tiles render in the new grid layout
- Tiles sorted by daily change (biggest gainer first)
- Placeholder dashed sparkline visible on each tile
- Sentiment strip (green/red/grey) visible on left edge
- Price color matches sentiment

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "redesign: watchlist tile grid — at-rest layout with placeholder sparklines"
```

---

## Chunk 5: Watchlist Tiles — Expanded

### Task 6: Expanded tile CSS + expand/collapse JS

**Files:**
- Modify: `src/main/resources/static/index.html` (expanded tile CSS, `toggleTile()`, `switchTf()`, `exp-close` button CSS)

- [ ] **Step 1: Add expanded tile CSS**

```css
/* ── Expanded tile ──────────────────────── */
.tile-expanded-body { display: none; flex-direction: column; margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border-mid); gap: 14px; }
.wl-tile.expanded .tile-expanded-body { display: flex; }
/* Timeframe toggle */
.exp-chart { width: 100%; }
.exp-chart-top { display: flex; align-items: center; justify-content: flex-end; margin-bottom: 8px; }
.tf-toggle { display: flex; gap: 2px; }
.tf-btn {
  font-family: var(--mono); font-size: 9px; letter-spacing: .5px;
  padding: 3px 9px; border-radius: 3px;
  background: none; border: 1px solid var(--border);
  color: var(--text-dim); cursor: pointer;
  transition: color .15s, border-color .15s, background .15s, box-shadow .18s;
}
.tf-btn:hover { border-color: var(--border-mid); color: var(--text-mid); }
.tf-btn.active { border-color: rgba(78,221,138,.3); color: var(--green); background: rgba(78,221,138,.08); box-shadow: var(--glow-sm); }
/* Expanded sparkline */
.exp-spark { display: block; width: 100%; height: 64px; overflow: visible; }
.exp-xaxis { display: flex; justify-content: space-between; margin-top: 5px; padding: 0 1px; }
.exp-xaxis span { font-size: 8px; color: var(--text-muted); letter-spacing: .3px; }
/* Fundamentals grid */
.exp-funds { display: grid; grid-template-columns: repeat(3, 1fr); gap: 7px; }
.exp-fund { background: rgba(0,0,0,.25); border: 1px solid rgba(78,221,138,.08); border-radius: 5px; padding: 7px 9px; transition: border-color .15s, box-shadow .18s; }
.exp-fund:hover { border-color: rgba(78,221,138,.2); box-shadow: var(--glow-sm); }
.exp-fund-label { font-size: 7.5px; font-weight: 500; letter-spacing: .8px; text-transform: uppercase; color: var(--text-dim); margin-bottom: 3px; }
.exp-fund-val { font-size: 12px; color: var(--text); font-variant-numeric: tabular-nums; }
.exp-fund-val.green { color: var(--green); }
.exp-fund-val.amber { color: var(--amber); }
.exp-fund-val.red   { color: var(--red); }
/* News section */
.exp-news-section { padding-top: 12px; border-top: 1px solid var(--border); }
.exp-news-label { font-size: 8px; font-weight: 500; letter-spacing: 1px; text-transform: uppercase; color: var(--text-dim); margin-bottom: 8px; }
.exp-news-item { padding: 6px 0; border-bottom: 1px solid rgba(255,255,255,.04); cursor: pointer; transition: padding-left .15s; }
.exp-news-item:last-child { border-bottom: none; }
.exp-news-item:hover { padding-left: 4px; }
.exp-news-headline { font-size: 10px; color: var(--text-mid); line-height: 1.5; margin-bottom: 2px; transition: color .15s; }
.exp-news-item:hover .exp-news-headline { color: var(--text); }
.exp-news-meta { font-size: 8.5px; color: var(--text-dim); }
/* Close button */
.exp-close {
  position: absolute; top: 10px; right: 10px;
  background: rgba(255,255,255,.05); border: 1px solid var(--border);
  color: var(--text-dim); font-size: 11px; cursor: pointer;
  padding: 2px 6px; border-radius: 3px; font-family: var(--mono);
  transition: color .15s, box-shadow .18s, border-color .15s;
  z-index: 1;
}
.exp-close:hover { color: var(--text); border-color: var(--border-mid); box-shadow: var(--glow-sm); }
```

- [ ] **Step 2: Add x-axis label constants and the expanded chart skeleton helper**

```javascript
const TF_LABELS = {
  '1d': ['9:30','11:00','1:00P','3:00P','Close'],
  '5d': ['Mon','Tue','Wed','Thu','Fri'],
  '3m': ['Jan','Feb','Mar 1','Mar 8','Now'],
};

function expandedChartSkeletonHTML(sym) {
  return `<div class="exp-chart">
    <div class="exp-chart-top">
      <div class="tf-toggle">
        <button class="tf-btn active" onclick="switchTf('${sym}','1d',event)">1D</button>
        <button class="tf-btn"        onclick="switchTf('${sym}','5d',event)">5D</button>
        <button class="tf-btn"        onclick="switchTf('${sym}','3m',event)">3M</button>
      </div>
    </div>
    <svg class="exp-spark" id="exp-spark-${sym}" viewBox="0 0 240 64" preserveAspectRatio="none">
      <line x1="0" y1="16" x2="240" y2="16" stroke="rgba(255,255,255,.04)" stroke-width="1" stroke-dasharray="3,5"/>
      <line x1="0" y1="32" x2="240" y2="32" stroke="rgba(255,255,255,.04)" stroke-width="1" stroke-dasharray="3,5"/>
      <line x1="0" y1="48" x2="240" y2="48" stroke="rgba(255,255,255,.04)" stroke-width="1" stroke-dasharray="3,5"/>
      <line class="spark-midline" x1="0" y1="32" x2="240" y2="32"
        stroke="rgba(255,255,255,.14)" stroke-width="1" stroke-dasharray="6,6"/>
    </svg>
    <div class="exp-xaxis" id="exp-xaxis-${sym}">
      ${TF_LABELS['1d'].map(l => `<span>${l}</span>`).join('')}
    </div>
    <div style="text-align:center;margin-top:4px;font-size:9px;color:var(--text-muted)">— chart data unavailable</div>
  </div>`;
}
```

> **Note:** On day 1, sparklines show the placeholder. When real candle data is wired in (follow-on task), `expandedChartSkeletonHTML` will be replaced with a version that draws real SVG paths.

- [ ] **Step 3: Add `toggleTile()` and `switchTf()`**

```javascript
/* ── Tile expand/collapse ───────────────── */
function toggleTile(sym, e) {
  if (e) e.stopPropagation();
  const tile = document.getElementById('wl-' + sym);
  if (!tile) return;
  const wasExpanded = tile.classList.contains('expanded');

  // Collapse all tiles
  document.querySelectorAll('.wl-tile.expanded').forEach(t => {
    t.classList.remove('expanded');
    const closeBtn = t.querySelector('.exp-close');
    if (closeBtn) closeBtn.style.display = 'none';
  });

  if (!wasExpanded) {
    tile.classList.add('expanded');
    const closeBtn = tile.querySelector('.exp-close');
    if (closeBtn) closeBtn.style.display = 'block';

    // Populate expanded body if not already done
    const body = tile.querySelector('.tile-expanded-body');
    if (body && !body.dataset.loaded) {
      body.innerHTML = expandedChartSkeletonHTML(sym) +
        `<div class="exp-funds" id="exp-funds-${sym}">
          <div class="exp-fund"><div class="exp-fund-label">Loading</div><div class="exp-fund-val">—</div></div>
        </div>
        <div class="exp-news-section">
          <div class="exp-news-label">Related News</div>
          <div id="exp-news-${sym}" style="color:var(--text-muted);font-size:9px">Loading…</div>
        </div>`;
      body.style.display = 'flex';
      body.dataset.loaded = 'true';
      loadTileDetails(sym);
    } else if (body) {
      body.style.display = 'flex';
    }
  }
}

function switchTf(sym, tf, e) {
  if (e) e.stopPropagation();
  const tile = document.getElementById('wl-' + sym);
  if (!tile) return;
  tile.querySelectorAll('.tf-btn').forEach(b => b.classList.remove('active'));
  const btn = tile.querySelector(`.tf-btn[onclick*="'${tf}'"]`);
  if (btn) btn.classList.add('active');
  const xaxis = document.getElementById('exp-xaxis-' + sym);
  if (xaxis) xaxis.innerHTML = TF_LABELS[tf].map(l => `<span>${l}</span>`).join('');
  // Sparkline path swap will be wired in when real data is available
}
```

- [ ] **Step 4: Build and verify**

```bash
./gradlew build
```
Expected: `BUILD SUCCESSFUL`.

Run `./gradlew bootRun`. Click on a watchlist tile. Verify:
- Tile expands to span 2 columns
- Timeframe toggle renders (1D active by default)
- Switching timeframes updates x-axis labels
- Placeholder chart shows dashed midline + "chart data unavailable"
- ✕ button collapses the tile
- Only one tile can be expanded at a time
- `glowpulse` animation breathes on the expanded tile

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "redesign: watchlist tile expand/collapse with chart skeleton"
```

---

### Task 7: Expanded tile data — fundamentals + news (lazy fetch)

**Files:**
- Modify: `src/main/resources/static/index.html` (`loadTileDetails()` function)

The expanded tile fetches two endpoints when first opened:
- `GET /api/quotes/{symbol}/overview` → market cap, P/E, sector, 52W High/Low, beta
- `GET /api/quotes/{symbol}/history` → last daily bar for Open/High/Low/Prev Close/Volume

Both are fetched in parallel and populate the fundamentals grid. News is not yet available via API; the section shows a "no news available" state for now.

- [ ] **Step 1: Add `loadTileDetails()` function**

```javascript
async function loadTileDetails(sym) {
  const [overviewRes, historyRes] = await Promise.allSettled([
    get('/api/quotes/' + sym + '/overview'),
    get('/api/quotes/' + sym + '/history'),
  ]);

  const ov   = overviewRes.status  === 'fulfilled' ? overviewRes.value  : null;
  const hist = historyRes.status   === 'fulfilled' ? historyRes.value   : null;
  const bar  = hist?.bars?.length  ? hist.bars[hist.bars.length - 1]    : null;

  // Build fundamentals cells
  const fmtLarge = n => {
    if (n == null) return '—';
    if (n >= 1e12) return '$' + (n / 1e12).toFixed(2) + 'T';
    if (n >= 1e9)  return '$' + (n / 1e9).toFixed(2)  + 'B';
    if (n >= 1e6)  return '$' + (n / 1e6).toFixed(2)  + 'M';
    return '$' + Number(n).toLocaleString();
  };

  // Sixth metric: market cap for most tickers; P/E if no market cap
  const sixthLabel = ov?.market_cap != null ? 'Mkt Cap' : ov?.pe_ratio != null ? 'P/E' : 'Beta';
  const sixthValue = ov?.market_cap != null
    ? fmtLarge(ov.market_cap)
    : ov?.pe_ratio != null
    ? ov.pe_ratio.toFixed(1) + 'x'
    : ov?.beta != null ? ov.beta.toFixed(2) : '—';

  const cells = [
    { l: 'Open',     v: bar?.open      != null ? money(bar.open)      : '—', c: '' },
    { l: 'High',     v: bar?.high      != null ? money(bar.high)      : '—', c: 'green' },
    { l: 'Low',      v: bar?.low       != null ? money(bar.low)       : '—', c: 'red' },
    { l: 'Prev Cls', v: bar?.close     != null ? money(bar.close)     : '—', c: '' },
    { l: 'Volume',   v: bar?.volume    != null ? num(bar.volume)      : '—', c: '' },
    { l: sixthLabel, v: sixthValue,                                           c: 'amber' },
  ];

  const fundsEl = document.getElementById('exp-funds-' + sym);
  if (fundsEl) {
    fundsEl.innerHTML = cells.map(c => `
      <div class="exp-fund">
        <div class="exp-fund-label">${c.l}</div>
        <div class="exp-fund-val ${c.c}">${c.v}</div>
      </div>`).join('');
  }

  // News: not yet available via API
  const newsEl = document.getElementById('exp-news-' + sym);
  if (newsEl) {
    newsEl.innerHTML = `<div style="font-size:9px;color:var(--text-muted);padding:4px 0">No news available.</div>`;
  }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew build
```
Expected: `BUILD SUCCESSFUL`.

Run `./gradlew bootRun`. Expand a tile. Verify:
- Overview fundamentals populate (Market Cap or P/E visible)
- Bar data populates (Open/High/Low/Prev Close/Volume from last daily bar)
- "No news available" shows in news section
- No console errors — both API calls handled gracefully if they return 404/null

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "redesign: lazy-load fundamentals on tile expand via overview + history APIs"
```

---

## Chunk 6: Secondary Pages

### Task 8: Quotes page theme

**Files:**
- Modify: `src/main/resources/static/index.html` (quotes page HTML, quotes-related CSS)

- [ ] **Step 1: Replace quotes page CSS**

Remove existing `.field-row`, `.field`, `.btn-primary`, `#quoteResult`, `.overview-grid` etc. styles and replace with terminal-dark versions:
```css
.field-row { display: flex; gap: 10px; margin-bottom: 14px; align-items: center; }
.field {
  flex: 1; background: var(--bg-card); border: 1px solid var(--border);
  border-radius: 6px; padding: 8px 12px; color: var(--text);
  font-family: var(--mono); font-size: 12px;
  transition: border-color .18s, box-shadow .18s;
  outline: none;
}
.field:focus { border-color: var(--border-mid); box-shadow: var(--glow-ring); }
.field::placeholder { color: var(--text-muted); }
.btn-primary {
  background: rgba(78,221,138,.12); border: 1px solid rgba(78,221,138,.3);
  color: var(--green); font-family: var(--mono); font-size: 11px;
  padding: 8px 16px; border-radius: 6px; cursor: pointer;
  transition: background .15s, box-shadow .18s;
}
.btn-primary:hover { background: rgba(78,221,138,.18); box-shadow: var(--glow-sm); }
.btn-primary:disabled { opacity: .4; cursor: default; }
.quota-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.quota-track { flex: 1; height: 3px; background: var(--bg-card); border-radius: 2px; overflow: hidden; }
.quota-fill { height: 100%; background: var(--green); transition: width .3s; }
.quota-fill.warn { background: var(--amber); }
.quota-fill.crit { background: var(--red); }
.quota-label { font-size: 9px; color: var(--text-muted); white-space: nowrap; }
/* Quote result card */
.quote-card {
  background: var(--bg-card); border: 1px solid var(--border);
  border-radius: 8px; padding: 20px 24px; margin-bottom: 16px;
  box-shadow: inset 0 1px 0 rgba(78,221,138,.06);
}
.quote-card:hover { border-color: var(--border-mid); box-shadow: var(--glow-ring); }
.qc-symbol { font-size: 22px; font-weight: 400; color: var(--text-mid); letter-spacing: .5px; margin-bottom: 4px; }
.qc-price  { font-size: 36px; font-weight: 300; letter-spacing: -1px; font-variant-numeric: tabular-nums; color: var(--amber); margin-bottom: 6px; }
.qc-change.pos { color: var(--green); }
.qc-change.neg { color: var(--red); }
.qc-meta   { font-size: 9.5px; color: var(--text-muted); margin-top: 4px; }
/* Overview grid */
.overview-grid {
  background: var(--bg-card); border: 1px solid var(--border);
  border-radius: 8px; padding: 16px;
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px;
}
.ov-cell { padding: 8px; border: 1px solid rgba(78,221,138,.06); border-radius: 5px; }
.ov-cell:hover { border-color: rgba(78,221,138,.15); box-shadow: var(--glow-sm); }
.ov-label { font-size: 7.5px; font-weight: 500; letter-spacing: .8px; text-transform: uppercase; color: var(--text-dim); margin-bottom: 3px; }
.ov-value { font-size: 12px; color: var(--text); font-variant-numeric: tabular-nums; }
```

- [ ] **Step 2: Update `fetchQuote()` / quote result rendering**

Find the existing `fetchQuote()` function (around line 1440). Update the HTML it produces to use the new CSS classes: `quote-card`, `qc-symbol`, `qc-price`, `qc-change`, `qc-meta`, `overview-grid`, `ov-cell`, `ov-label`, `ov-value`. Refer to the existing function's structure — just swap old class names for new ones.

- [ ] **Step 3: Build and verify**

```bash
./gradlew build
```
Expected: `BUILD SUCCESSFUL`.

Run `./gradlew bootRun`. Navigate to Quotes. Enter a ticker. Verify quote card renders in terminal dark theme.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "redesign: quotes page theme"
```

---

### Task 9: Portfolio page theme

**Files:**
- Modify: `src/main/resources/static/index.html` (portfolio CSS, portfolio HTML structure, preserve `fitQuoteText`)

- [ ] **Step 1: Replace portfolio CSS**

Remove existing `.port-top-row`, `.daily-quote`, `.daily-quote-mark`, `.daily-quote-text`, `.daily-quote-attr`, portfolio table styles. Replace with:
```css
.port-top-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
  align-items: start;
}
.daily-quote {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 20px 24px;
  display: flex;
  gap: 14px;
  align-items: flex-start;
  min-height: 120px;
  box-shadow: inset 0 1px 0 rgba(78,221,138,.06);
}
.daily-quote:hover { border-color: var(--border-mid); box-shadow: var(--glow-ring); }
.daily-quote-mark { font-family: var(--serif); font-size: 42px; line-height: 1; color: var(--green-dim); flex-shrink: 0; margin-top: -6px; }
.daily-quote-text {
  font-size: clamp(10px, 2.5vw, 18px);
  font-style: italic;
  color: var(--text-mid);
  line-height: 1.5;
  font-family: var(--serif);
  font-weight: 300;
  font-optical-sizing: auto;
  overflow: hidden;
}
.daily-quote-attr { font-size: 9px; color: var(--text-muted); letter-spacing: .8px; text-transform: uppercase; margin-top: 6px; }
/* Portfolio table */
.table-wrap { background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; overflow: hidden; }
.data-table { width: 100%; border-collapse: collapse; font-size: 11px; }
.data-table th { padding: 9px 14px; text-align: left; font-size: 8px; font-weight: 500; letter-spacing: .9px; text-transform: uppercase; color: var(--text-dim); border-bottom: 1px solid var(--border); }
.data-table td { padding: 10px 14px; border-bottom: 1px solid rgba(78,221,138,.05); color: var(--text-mid); font-variant-numeric: tabular-nums; }
.data-table tr:last-child td { border-bottom: none; }
.data-table tr:hover td { background: rgba(78,221,138,.03); }
.td-pos { color: var(--green); }
.td-neg { color: var(--red); }
.td-amber { color: var(--amber); }
```

- [ ] **Step 2: Update portfolio stat grid container**

In the portfolio page HTML, update `<div class="stat-grid port-stat-grid" id="port-stats">` to:
```html
<div class="stat-row" id="port-stats"></div>
```

- [ ] **Step 3: Verify `fitQuoteText()` is preserved unchanged**

`fitQuoteText()` targets `#dailyQuoteText` and uses a ResizeObserver. Do not modify it. Only the CSS changes. Confirm the function still exists and the HTML IDs `#dailyQuote` and `#dailyQuoteText` are present in the portfolio page HTML.

- [ ] **Step 4: Update `loadPortfolio()` table rendering**

Find `loadPortfolio()` (around line 1533). Update the table HTML it produces to use new class names: `data-table`, `td-pos`, `td-neg`, `td-amber`. Keep all logic unchanged.

- [ ] **Step 5: Build and verify**

```bash
./gradlew build
```
Expected: `BUILD SUCCESSFUL`.

Run `./gradlew bootRun`. Navigate to Portfolio. Verify:
- Stat cards render in 2-column half of the top row
- Buffett quote renders in the other half
- `fitQuoteText()` sizes the quote text dynamically
- Positions table renders with terminal dark styling

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "redesign: portfolio page theme — preserve fitQuoteText"
```

---

### Task 10: Finance page theme

**Files:**
- Modify: `src/main/resources/static/index.html` (finance CSS, `loadFinance()` table rendering)

- [ ] **Step 1: Replace finance page CSS**

Remove existing finance-specific styles (`#fin-stats`, `#fin-table`, `#catFilter` etc.) and apply the same `stat-row`, `table-wrap`, `data-table`, `field-row`, `field` patterns already added in prior tasks. The finance page reuses these shared components — no new CSS needed beyond what Tasks 4, 7, and 8 already added.

Update the `catFilter` select and `filterBtn` to use the `field` and `btn-primary` classes if not already applied.

- [ ] **Step 2: Update `loadFinance()` table rendering**

Find `loadFinance()` (around line 1622). Update the table HTML to use `data-table`, `td-pos`, `td-neg`, `td-amber`. Keep all logic unchanged. Update the category spending chart colors if using canvas: replace old forest-green palette with `#4edd8a` for positive bars and `#f07868` for negative.

- [ ] **Step 3: Build and verify**

```bash
./gradlew build
```
Expected: `BUILD SUCCESSFUL`.

Run `./gradlew bootRun`. Navigate to Finance. Verify transactions table renders correctly in the new theme.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "redesign: finance page theme"
```

---

## Chunk 7: Auth, Modals, and Polish

### Task 11: Auth overlay + Add Position modal

**Files:**
- Modify: `src/main/resources/static/index.html` (auth overlay HTML/CSS, modal HTML/CSS)

- [ ] **Step 1: Replace auth overlay CSS**

Remove existing `.overlay`, `.lock-box` etc. styles. Replace with:
```css
.overlay {
  position: fixed; inset: 0;
  background: var(--bg);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
}
.lock-box {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 36px 40px;
  width: 340px;
  box-shadow: var(--glow-ring);
  display: flex; flex-direction: column; gap: 20px;
}
.lock-title {
  font-family: var(--serif);
  font-style: italic;
  font-size: 28px;
  font-weight: 300;
  color: var(--text);
  font-optical-sizing: auto;
}
.lock-sub { font-size: 11px; color: var(--text-dim); }
.lock-err { font-size: 10px; color: var(--red); min-height: 14px; }
```

- [ ] **Step 2: Replace modal CSS**

```css
.modal-backdrop {
  display: none; position: fixed; inset: 0;
  background: rgba(0,0,0,.6); backdrop-filter: blur(4px);
  align-items: center; justify-content: center; z-index: 500;
}
.modal-backdrop.open { display: flex; }
.modal {
  background: var(--bg-card);
  border: 1px solid var(--border-mid);
  border-radius: 10px;
  padding: 28px 32px;
  width: 380px;
  box-shadow: var(--glow-ring);
}
.modal h3 { font-size: 14px; font-weight: 500; color: var(--text); margin-bottom: 20px; }
.field-group { display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px; }
.field-label { font-size: 9px; font-weight: 500; letter-spacing: .8px; text-transform: uppercase; color: var(--text-dim); margin-bottom: 4px; }
.modal-err { font-size: 10px; color: var(--red); min-height: 14px; margin-bottom: 8px; }
.modal-actions { display: flex; gap: 8px; justify-content: flex-end; }
```

- [ ] **Step 3: Update modal open/close**

Find `openAddPosition()` and `closeAddPosition()`. Update them to use `.open` class instead of `style.display`:
```javascript
function openAddPosition()  { document.getElementById('addPositionModal').classList.add('open'); }
function closeAddPosition() { document.getElementById('addPositionModal').classList.remove('open'); }
```

- [ ] **Step 4: Replace empty/spinner/state-box CSS**

```css
.state-box { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 40px; gap: 10px; }
.state-icon { font-size: 28px; opacity: .5; }
.state-title { font-size: 12px; color: var(--text-dim); }
.state-sub { font-size: 10px; color: var(--text-muted); text-align: center; }
.spinner {
  display: inline-block; width: 14px; height: 14px;
  border: 1.5px solid rgba(78,221,138,.2);
  border-top-color: var(--green);
  border-radius: 50%;
  animation: spin .7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
```

- [ ] **Step 5: Build and verify**

```bash
./gradlew build
```
Expected: `BUILD SUCCESSFUL`.

Run `./gradlew bootRun`. Open `http://localhost:8080` without a stored key. Verify:
- Lock screen renders in terminal dark theme
- Enter a valid API key — dashboard launches
- Navigate to Portfolio, click "+ Add Position" — modal opens with terminal dark styling

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "redesign: auth overlay and modal theme"
```

---

### Task 12: Final cleanup and full verification

**Files:**
- Modify: `src/main/resources/static/index.html` (remove dead CSS, verify no old class references)

- [ ] **Step 1: Remove dead CSS**

Search for any remaining references to old CSS variables (`--forest-deep`, `--forest-mid`, `--cream`, `--sage`, `--mist`, etc.) and old class names that no longer exist in the HTML. Remove them.

```bash
grep -n "forest\|cream\|sage-light\|mist\|radius-sm\|radius-md\|radius-lg\|shadow-sm\|shadow-md\|Inter" \
  src/main/resources/static/index.html
```

Remove any lines that appear only as leftover CSS.

- [ ] **Step 2: Full build + test**

```bash
./gradlew spotlessApply
./gradlew build
```
Expected: both pass with no errors.

- [ ] **Step 3: Visual verification checklist**

Run `./gradlew bootRun`. Walk through all pages and verify against the mockup at `.superpowers/brainstorm/2598-1773700949/dashboard-mockup-v8.html`:

**Dashboard:**
- [ ] Sidebar auto-collapses after 4s, expands on hover
- [ ] Ticker bar scrolls, pauses on hover
- [ ] Sidebar brand border aligns with topbar border
- [ ] Stat cards show in 4-column grid with correct colors
- [ ] Watchlist tiles sorted by daily change (biggest first)
- [ ] Sentiment strip visible on each tile
- [ ] Placeholder dashed sparkline on all tiles
- [ ] Click tile → expands to span 2, fundamentals load, timeframe toggle works
- [ ] Switching timeframe updates x-axis labels
- [ ] Clicking expanded tile again (or ✕) collapses it
- [ ] Only one tile expanded at a time

**Quotes:** Quote card + overview grid render in terminal dark theme

**Portfolio:** Stat cards + Buffett quote side by side, quote text sizes dynamically

**Finance:** Transactions table in terminal dark theme

**Auth:** Lock screen is terminal dark, modal is themed

- [ ] **Step 4: Final commit**

```bash
git add src/main/resources/static/index.html
git commit -m "redesign: final cleanup — remove dead CSS, full verification pass"
```

---

## Summary

| Chunk | Tasks | What it delivers |
|-------|-------|-----------------|
| 1 | 1 | Design tokens, fonts, animations |
| 2 | 2–3 | Sidebar collapse, ticker bar, topbar |
| 3 | 4 | Stat cards with trend badges |
| 4 | 5 | Watchlist tiles at rest — sorted, sentiment strip, placeholder sparklines |
| 5 | 6–7 | Tile expand/collapse, fundamentals lazy-load |
| 6 | 8–10 | Quotes, Portfolio, Finance themes |
| 7 | 11–12 | Auth, modal, cleanup |

**What is explicitly deferred to a follow-on plan:**
- Live sparkline paths (requires `MarketDataProvider` candle history endpoint)
- News feed per ticker (requires a news API integration)
- Logo/brand mark redesign
