# Journal Page Design Spec

## Overview

A dedicated Journal page for the trading dashboard — an investing diary where the user writes daily free-form notes, tracks positions via inline ticker/tag linking, captures media, and monitors financial goals. The page is write-first: the editor is open and ready on load with no extra navigation step required.

---

## Visual Design

### Editor Surface
- Writing surface: glossy black `#000` with `box-shadow: inset 0 1px 0 rgba(255,255,255,.04)`
- Font: Inter, 16px, weight 300, upright (no italic default), `line-height: 2.0`
- Body text color: `#c8c0b0` (warm neutral)
- Placeholder text: `rgba(200,192,176,.18)`
- Rich text toolbar: bold, italic, link (minimal)
- **Note:** Inter is not currently imported in `index.html`. The implementation must add the Google Fonts import (`family=Inter:opsz,wght@14..32,300;14..32,400`). `#c8c0b0` is intentionally warmer than the existing `--text` CSS variable; this divergence is by design for the journal writing surface.

### Inline Neon Tokens
As the user types, `$TICKER` and `#tag` patterns are detected and wrapped in styled `<span>` elements. Each token is assigned a color deterministically (hash of the string → index into palette). The palette of 10 neons:

| Slot | Color | Hex |
|------|-------|-----|
| 0 | Green | `#4edd8a` |
| 1 | Cyan | `#00e5ff` |
| 2 | Orange | `#ff7b35` |
| 3 | Lime | `#b8ff3e` |
| 4 | Red | `#ff3864` |
| 5 | Violet | `#a78bfa` |
| 6 | Pink | `#ff2d9e` |
| 7 | Yellow | `#ffd23f` |
| 8 | Purple | `#b060ff` |
| 9 | Amber | `#f4b84a` |

Each token also receives a matching `text-shadow` glow. The same token always renders in the same color across all entries.

### Card / Container
Outer card: site-standard `#111a13` background, `1px solid rgba(78,221,138,.22)` border, `border-radius: 8px`. Consistent with all other dashboard widgets.

---

## Page Layout

The Journal page is added to the existing single-file SPA using the same structure as Dashboard, Quotes, Portfolio, and Finance. It does **not** introduce its own nav bar or shell — the shared sidebar, topbar, and ticker bar remain unchanged.

**Navigation integration:**
- Add a `nav-item` under the existing "Personal" nav-group in the sidebar (after Finance), with `data-page="journal"`:
```html
<div class="nav-item" data-page="journal">
  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.4">
    <rect x="2" y="1" width="10" height="12" rx="1.5"/>
    <path d="M5 4h4M5 7h4M5 10h2"/>
  </svg>
  <span class="nav-label">Journal</span>
</div>
```
- Add `'journal': 'Journal'` to the `TITLES` map.
- Add `if (page === 'journal') loadJournal();` alongside the existing page-load calls.

**Page content container:** `<div class="page" id="page-journal">` inside `<div class="content">`, identical to other pages.

**Two-column layout inside the content area:**

```
┌──────────────────────────────────┬─────────────────┐
│  TODAY'S ENTRY (editor, open)    │  CALENDAR       │
│                                  │  STATS          │
│  ──────────────────────────────  │  MOVERS         │
│                                  │  GOALS          │
│  PAST ENTRY (dimmed)             │  MOST WRITTEN   │
│                                  │                 │
│  PAST ENTRY (more dimmed)        │                 │
└──────────────────────────────────┴─────────────────┘
```

- Left column (~60%): Editor + scrollable entry feed
- Right column (~40%): Persistent dashboard context
- No extra click required to start writing

---

## Left Column: Entry Feed

### Today's Entry
- Always at top, always open — no click required
- Editor is a `contenteditable` div with real-time token detection
- Entry header shows date and entry number
- Footer: auto-detected tags displayed as neon pills + Save button
- Brighter card border than past entries (`rgba(78,221,138,.30)`)

### Past Entries
- Scroll below today's entry
- Progressive dimming: yesterday at `opacity: 0.55`, two days ago at `opacity: 0.28`, older entries at `opacity: 0.18`
- Each past entry shows: date, entry number, glossy black board with entry text (neons intact), tag pills

### Filter Mode
- Clicking any `$TICKER` or `#tag` token (in entries, sidebar, or calendar) filters the entry feed
- A dismissible banner appears: `Viewing: $NVDA (18 entries)  ×`
- Feed shows only matching entries, still progressively dimmed by age
- Clicking × returns to full feed

### Media Embeds
- Pasting a YouTube or Twitter/X URL into the editor auto-detects the URL
- URL is immediately replaced with an inline embed card showing: thumbnail, title, source domain
- Embed cards are stored as HTML in the entry body

---

## Right Column: Dashboard Context

### Calendar
- Current month only, dynamically generated
- 7-column grid (Mon–Sun), one square per day
- Color coding:
  - No entry: `rgba(78,221,138,.06)` hollow square
  - Entry written: `rgba(78,221,138,.45)` filled
  - Entry with media or 3+ tags: `#4edd8a` bright + subtle glow
- Clicking a day with an entry navigates/filters to that entry
- Current day highlighted with border

### Stats Row
Three mini-stat cards in a row:
- **Entries** — total journal entries (from `/api/journal/stats`)
- **Streak** — consecutive days with an entry (from `/api/journal/stats`)
- **Today** — portfolio day P&L. Computed client-side: fetch positions from `/api/portfolio/positions`, fetch a quote per ticker from `/api/quotes/{symbol}` (reused for Movers — one set of requests serves both panels). Compute weighted-average `change_pct` across positions weighted by market value (`quantity × price`). No new endpoint needed.

### Movers in Your Journal
- The frontend fetches positions from `/api/portfolio/positions` and quotes from `/api/quotes/{symbol}` for each (shared with the Stats Row "Today" computation above — one set of fetches serves both panels).
- Cross-references the resulting quotes against tickers found in journal entries (from the loaded entries list) — only tickers the user has written about are displayed.
- Sorts by absolute `change_pct`, shows: ticker (neon), % change today, entry count.
- No new backend endpoint required. Scoping to portfolio positions prevents stale tickers from appearing.

### Goals
- List of goals with progress bars
- Two types: **milestone** (e.g. "Hit $500K" — numeric target) and **habit** (e.g. "Write daily" — count/streak)
- Each goal shows: label, progress bar (neon-colored), current/target values
- **Edit mode**: an "Edit Goals" button toggles inline editing — add, modify, delete goals without leaving the page
- Progress bars use neon colors matching the goal type (green for habits, yellow for milestones, violet for custom)

### Most Written About
- Top tickers and tags ranked by entry count
- Inline mini bar chart (each row: token, proportional bar, count)
- Clicking a token triggers filter mode on the left column

---

## Backend

### Database — Four New Tables

Migration file: `src/main/resources/db/migration/V3__journal.sql`

**`journal_entries`**
```sql
CREATE TABLE journal_entries (
    id          BIGSERIAL PRIMARY KEY,
    body        TEXT NOT NULL,                                    -- HTML content
    entry_date  DATE NOT NULL DEFAULT CURRENT_DATE,              -- server-local date; used to determine "today's entry"
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_journal_entries_entry_date UNIQUE (entry_date)
);
```

**`journal_entry_tickers`** (normalized, H2-compatible — replaces `TEXT[]`)
```sql
CREATE TABLE journal_entry_tickers (
    entry_id    BIGINT NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    ticker      TEXT NOT NULL,
    PRIMARY KEY (entry_id, ticker)
);
```

**`journal_entry_tags`** (normalized, H2-compatible)
```sql
CREATE TABLE journal_entry_tags (
    entry_id    BIGINT NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    tag         TEXT NOT NULL,
    PRIMARY KEY (entry_id, tag)
);
```

**`journal_goals`**
```sql
CREATE TABLE journal_goals (
    id               BIGSERIAL PRIMARY KEY,
    label            TEXT NOT NULL,
    goal_type        TEXT NOT NULL CHECK (goal_type IN ('milestone','habit')),
    target_value     NUMERIC,            -- required for milestone; NULL for open-ended habits
    milestone_value  NUMERIC,            -- stored progress for milestone goals only
    deadline         DATE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
```

**Schema notes:**
- `TIMESTAMP WITH TIME ZONE` is used (not `TIMESTAMPTZ` shorthand) for H2 compatibility across dev/test profiles.
- `entry_date` is populated server-side at save time. "Today's entry" is the entry whose `entry_date` matches the current server date. The `UNIQUE (entry_date)` constraint enforces one entry per day at the DB level — the service layer `upserts` (load existing entry for today if present, otherwise create).
- Habit goal progress is derived at query time from `journal_entries` (e.g., count of distinct `entry_date` values in the current month for "Write daily"). `milestone_value` is only meaningful for `goal_type = 'milestone'`.

**Today's entry save flow (frontend):**
On journal page load the frontend calls `GET /api/journal/entries?entryDate=<today>`. If an entry exists, load it into the editor and retain the `id`. If no entry exists, the editor starts empty with no `id`. On first Save, call `POST /api/journal/entries` and store the returned `id`. All subsequent saves within the same session call `PUT /api/journal/entries/{id}`. This avoids duplicate rows while keeping the API clean.

### REST Endpoints

All under `/api/journal`, protected by existing `ApiKeyAuthFilter` in non-dev profiles.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/journal/entries` | List all entries, newest first. Optional `?ticker=NVDA`, `?tag=macro`, or `?entryDate=2026-03-17` filter. |
| `POST` | `/api/journal/entries` | Create entry. See request body below. |
| `PUT` | `/api/journal/entries/{id}` | Update entry. See request body below. |
| `DELETE` | `/api/journal/entries/{id}` | Delete entry |
| `GET` | `/api/journal/goals` | List all goals |
| `POST` | `/api/journal/goals` | Create goal |
| `PUT` | `/api/journal/goals/{id}` | Update goal (label, milestoneValue, targetValue, deadline) |
| `DELETE` | `/api/journal/goals/{id}` | Delete goal |
| `GET` | `/api/journal/stats` | Returns entry count, streak, calendar data, and most-mentioned tokens. See response shape below. |

**POST/PUT entry request body:**
```json
{
  "body": "<p>$NVDA holding strong...</p>",
  "entryDate": "2026-03-17"
}
```
Tickers and tags are **not** accepted from the client — the server extracts them from `body` as the authoritative source.

**GET `/api/journal/entries` entry response DTO (single object and list items):**
```json
{
  "id": 47,
  "body": "<p>$NVDA holding strong...</p>",
  "entryDate": "2026-03-17",
  "tickers": ["NVDA", "MSFT"],
  "tags": ["macro", "growth"],
  "createdAt": "2026-03-17T14:32:00Z",
  "updatedAt": "2026-03-17T15:01:00Z"
}
```

**POST `/api/journal/goals` request body:**
```json
{
  "label": "Hit $500K",
  "goalType": "milestone",
  "targetValue": 500000,
  "milestoneValue": 372000,
  "deadline": "2026-12-31"
}
```
`milestoneValue` and `deadline` are optional. For habit goals, `targetValue` is optional (open-ended); `milestoneValue` is ignored.

**GET `/api/journal/goals` response (array of goal objects):**
```json
[
  {
    "id": 1,
    "label": "Hit $500K",
    "goalType": "milestone",
    "targetValue": 500000,
    "currentProgress": 372000,
    "progressPct": 74.4,
    "deadline": "2026-12-31",
    "createdAt": "2026-01-01T00:00:00Z"
  },
  {
    "id": 2,
    "label": "Write daily",
    "goalType": "habit",
    "targetValue": 30,
    "currentProgress": 12,
    "progressPct": 40.0,
    "deadline": null,
    "createdAt": "2026-03-01T00:00:00Z"
  }
]
```
`currentProgress` is always returned by the service: for `milestone` goals it is `milestone_value` from the DB; for `habit` goals it is computed at query time (count of distinct `entry_date` values in the current calendar month). `progressPct` = `currentProgress / targetValue × 100` (null if `targetValue` is null). DELETE endpoints return `204 No Content`.

**GET `/api/journal/stats` response shape:**
```json
{
  "entryCount": 47,
  "currentStreak": 12,
  "calendar": {
    "2026-03-01": 0,
    "2026-03-15": 1,
    "2026-03-17": 2
  },
  "mostMentioned": [
    { "token": "$NVDA", "count": 18 },
    { "token": "#macro", "count": 9 }
  ]
}
```
Calendar activity levels: `0` = no entry, `1` = entry written, `2` = entry with media or 3+ tags.

### Ticker/Tag Extraction
Extraction happens server-side on `POST`/`PUT` of an entry. Parse the HTML body, find all `$WORD` and `#word` patterns, deduplicate, and store in the arrays. Client-side detection is for real-time visual rendering only — the server is the source of truth.

### Architecture
Follows existing patterns: `JournalController` (thin, implements generated interface) → `JournalService` → `JournalEntryRepository` + `JournalGoalRepository`. OpenAPI spec updated first, DTOs generated.

---

## Frontend

Single-file SPA addition to `src/main/resources/static/index.html`. The Journal page follows the exact same pattern as existing pages — a `<div class="page" id="page-journal">` container inside `<div class="content">`, a sidebar nav-item under "Personal", an entry in the `TITLES` map, and a `loadJournal()` call in the `go()` function. No new shell, topbar, or layout chrome is introduced.

### Token Detection (client-side)
On every `input` event in the `contenteditable` editor:
1. Capture caret position
2. Walk text nodes, find `$WORD` and `#tag` patterns not yet wrapped
3. Wrap matches in `<span class="neon-token" data-token="NVDA">$NVDA</span>`
4. Restore caret position
5. Apply CSS neon color via `data-token` hash → palette index

### Media Embed Detection
On `paste` event:
1. Check if pasted text is a YouTube or Twitter/X URL (regex)
2. If yes: prevent default, insert an embed card HTML snippet instead
3. YouTube card: thumbnail via `img.youtube.com/vi/{id}/mqdefault.jpg`, title placeholder, red play icon
4. Twitter card: rendered as a styled blockquote with source icon

### Contenteditable and Rich Text Interaction
- Bold, italic, and link toolbar actions use `document.execCommand` (deprecated but broadly supported) or the `Selection`/`Range` API.
- The token-wrapping `input` handler must skip nodes that are already `.neon-token` spans — do not re-wrap existing tokens on every keystroke.
- Non-embed pastes must strip foreign HTML: `paste` handler always calls `preventDefault`, reads `clipboardData.getData('text/plain')`, and inserts sanitized plain text via `document.execCommand('insertText')` (or `insertHTML` with sanitized content).
- The `body` saved to the server is the `innerHTML` of the editor div, preserving `<b>`, `<i>`, `<a>`, and `<span class="neon-token">` elements. No other tags are permitted.

### Calendar Rendering
On journal page load, fetch `/api/journal/stats`. Build the current month grid in JS — no library needed. Each day cell is a `<div>` with color class based on activity level returned from the API.

---

## Out of Scope
- Full YouTube/Twitter API integration (embed cards use thumbnail URLs only, no oEmbed)
- Calendar pagination / month navigation (current month only; future)
- Search across entry text (future)
- Entry export (future)
- Mobile layout (future)
