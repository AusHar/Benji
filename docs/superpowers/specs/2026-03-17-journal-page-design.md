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
- Pulls today's price movers from existing market data
- Cross-references against tickers mentioned in journal entries
- Shows: ticker (neon), % change today, entry count
- Only shows tickers the user has written about

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

### Database — Two New Tables

**`journal_entries`**
```sql
CREATE TABLE journal_entries (
    id          BIGSERIAL PRIMARY KEY,
    body        TEXT NOT NULL,          -- HTML content
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    tickers     TEXT[] NOT NULL DEFAULT '{}',  -- extracted e.g. ['NVDA','MSFT']
    tags        TEXT[] NOT NULL DEFAULT '{}'   -- extracted e.g. ['macro','growth']
);
```

**`journal_goals`**
```sql
CREATE TABLE journal_goals (
    id              BIGSERIAL PRIMARY KEY,
    label           TEXT NOT NULL,
    goal_type       TEXT NOT NULL CHECK (goal_type IN ('milestone','habit')),
    target_value    NUMERIC,
    current_value   NUMERIC NOT NULL DEFAULT 0,
    deadline        DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Both tables added via Flyway migration following the existing `V<N>__<desc>.sql` convention.

### REST Endpoints

All under `/api/journal`, protected by existing `ApiKeyAuthFilter` in non-dev profiles.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/journal/entries` | List all entries, newest first. Optional `?ticker=NVDA` or `?tag=macro` filter. |
| `POST` | `/api/journal/entries` | Create entry. Body: `{ body, tickers[], tags[] }` |
| `PUT` | `/api/journal/entries/{id}` | Update entry body/tags |
| `DELETE` | `/api/journal/entries/{id}` | Delete entry |
| `GET` | `/api/journal/goals` | List all goals |
| `POST` | `/api/journal/goals` | Create goal |
| `PUT` | `/api/journal/goals/{id}` | Update goal (label, progress, target) |
| `DELETE` | `/api/journal/goals/{id}` | Delete goal |
| `GET` | `/api/journal/stats` | Returns: entry count, current streak, calendar data (date → activity level), most-mentioned tickers/tags |

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

### Calendar Rendering
On journal page load, fetch `/api/journal/stats`. Build the current month grid in JS — no library needed. Each day cell is a `<div>` with color class based on activity level returned from the API.

---

## Out of Scope
- Full YouTube/Twitter API integration (embed cards use thumbnail URLs only, no oEmbed)
- Search across entry text (future)
- Entry export (future)
- Mobile layout (future)
