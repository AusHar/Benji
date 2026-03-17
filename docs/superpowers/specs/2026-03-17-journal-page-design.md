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

Two-column layout:

```
┌─────────────────────────────────────┬──────────────────┐
│  NAV BAR                                               │
├─────────────────────────────────────┬──────────────────┤
│                                     │                  │
│  TODAY'S ENTRY (editor, open)       │  CALENDAR        │
│                                     │  STATS           │
│  ─────────────────────────────────  │  MOVERS          │
│                                     │  GOALS           │
│  PAST ENTRY (dimmed)                │  MOST WRITTEN    │
│                                     │                  │
│  PAST ENTRY (more dimmed)           │                  │
│                                     │                  │
└─────────────────────────────────────┴──────────────────┘
```

- Left column (~60%): Editor + scrollable entry feed
- Right column (~40%): Persistent dashboard context
- No page navigation required to start writing

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
- **Entries** — total journal entries
- **Streak** — consecutive days with an entry (derived from calendar)
- **Today** — portfolio P&L today (reuses existing market data)

### Movers in Your Journal
- Pulls today's price movers from existing market data (same source as the dashboard ticker bar)
- Cross-references against **current portfolio positions only** (holdings in the existing portfolio data) that the user has also written about in journal entries
- Shows: ticker (neon), % change today, entry count
- Scoping to portfolio positions prevents the panel from showing stale tickers the user mentioned once and no longer holds

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
    body        TEXT NOT NULL,                              -- HTML content
    entry_date  DATE NOT NULL DEFAULT CURRENT_DATE,        -- server-local date; used to determine "today's entry"
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
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
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Schema notes:**
- `entry_date` is populated server-side at save time. "Today's entry" is the entry whose `entry_date` matches the current server date. One entry per `entry_date` (enforced at the service layer — if an entry exists for today, the editor loads it for editing rather than creating a new row).
- Habit goal progress is derived at query time from `journal_entries` (e.g., count of distinct `entry_date` values in the current month for "Write daily"). `milestone_value` is only meaningful for `goal_type = 'milestone'`.

### REST Endpoints

All under `/api/journal`, protected by existing `ApiKeyAuthFilter` in non-dev profiles.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/journal/entries` | List all entries, newest first. Optional `?ticker=NVDA` or `?tag=macro` filter. |
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

Single-file SPA addition to `src/main/resources/static/index.html`. New `#journal` page section added alongside existing `#dashboard`, `#portfolio` pages. Navigation tab added.

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
