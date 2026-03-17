# Terminal Dark Redesign — Design Spec

**Date:** 2026-03-16
**Scope:** Full visual redesign of the Benji trading dashboard frontend (`src/main/resources/static/index.html`)
**Reference mockup:** `.superpowers/brainstorm/2598-1773700949/dashboard-mockup-v8.html`

---

## 1. Overview

Replace the current frontend with a terminal-dark aesthetic: deep near-black backgrounds, neon green as the single accent color, IBM Plex Mono for all data and UI text, and Fraunces italic for brand and page titles only. The redesign introduces an interactive watchlist tile system as the centerpiece of the dashboard, while applying the new theme consistently across all four pages (Dashboard, Quotes, Portfolio, Finance).

---

## 2. Design Language

### 2.1 Color System

```css
--bg:           #050a07   /* page background */
--bg-mid:       #09100b   /* sidebar, topbar */
--bg-card:      #111a13   /* card/tile default */
--bg-card-hi:   #172019   /* card/tile hover/active */

--border:       rgba(78,221,138,.11)   /* default border */
--border-mid:   rgba(78,221,138,.22)   /* hover border */
--border-hi:    rgba(78,221,138,.38)   /* active/expanded border */

--green:        #4edd8a   /* primary accent */
--green-dim:    rgba(78,221,138,.65)
--green-faint:  rgba(78,221,138,.07)

--red:          #f07868   /* negative/loss */
--amber:        #e8b45a   /* portfolio values, neutral-positive */

--text:         #f2faf4   /* primary text */
--text-mid:     #c0d8c8   /* secondary text */
--text-dim:     #85aa92   /* tertiary / labels */
--text-muted:   #607a6c   /* timestamps, nav groups */
```

### 2.2 Glow System

Three levels of neon glow applied via `box-shadow`:

```css
--glow-sm:      0 0 8px rgba(78,221,138,.18), 0 0 2px rgba(78,221,138,.25)
--glow-ring:    0 0 0 1px rgba(78,221,138,.3), 0 0 14px rgba(78,221,138,.2), 0 0 30px rgba(78,221,138,.08)
--glow-ring-hi: 0 0 0 1px rgba(78,221,138,.45), 0 0 20px rgba(78,221,138,.28), 0 0 44px rgba(78,221,138,.12)
```

- `glow-sm`: nav items, buttons, badges on hover
- `glow-ring`: card/tile hover state
- `glow-ring-hi`: focused/expanded tile, persistent + animated

**`glowpulse` animation** — breathes the expanded tile glow on a 3s ease-in-out loop:

```css
@keyframes glowpulse {
  0%,100% { box-shadow: var(--glow-ring-hi); }
  50%      { box-shadow: 0 0 0 1px rgba(78,221,138,.5), 0 0 24px rgba(78,221,138,.3), 0 0 50px rgba(78,221,138,.12); }
}
```

**`hum` animation** — pulses the live indicator dot and connection badge dot between a narrow and wide glow radius:
- Live pill dot: 2s loop
- Connection badge dot: 2.5s loop

### 2.3 Typography

| Use | Font | Weight | Style |
|-----|------|--------|-------|
| Brand name ("Benji"), page titles | Fraunces | 300 | italic, optical-sizing: auto |
| All other text — labels, values, nav, data | IBM Plex Mono | 300 / 400 / 500 | normal |

Font sizes follow a strict scale: 7.5px (nav groups), 8–9px (labels/hints), 10–11px (secondary data), 13px (base body), 18px (tile prices), 21px (topbar title / brand), 22px (stat card values).

### 2.4 Scrollbar

Override webkit scrollbar: 4px width, green-tinted thumb (`rgba(78,221,138,.18)`), rounded. Applied to all scrollable content areas.

---

## 3. Global Shell

### 3.1 Layout

```
┌─────────────┬────────────────────────────────┐
│   Sidebar   │         Ticker Bar (28px)       │
│  (192px or  ├────────────────────────────────┤
│   54px col) │         Topbar (50px)           │
│             ├────────────────────────────────┤
│             │         Content (scrollable)    │
└─────────────┴────────────────────────────────┘
```

- The sidebar brand section is exactly 78px tall (28px + 50px) so its bottom border aligns flush with the topbar's bottom border.

### 3.2 Sidebar

- **Expanded:** 192px wide. Shows brand mark, nav group labels, nav item labels, connection badge text.
- **Collapsed:** 54px wide (icons only). Labels and group headings fade to opacity 0; icons remain.
- **Behavior:** The collapse timer starts immediately on page load (not on first mouse interaction). The sidebar will auto-collapse 4 seconds after the page renders. On `mouseenter`, the timer is cancelled and the sidebar re-expands immediately. On `mouseleave`, the 4-second timer restarts. Uses CSS `width` transition (cubic-bezier `.4,0,.2,1`, 300ms).
- **Nav items:** Dashboard, Quotes, Portfolio, Finance. Active item highlighted with green tint + `glow-sm`.
- **Footer:** "Connected" badge with animated dot. Dot uses `hum` animation at 2.5s.

### 3.3 Ticker Bar

- Height: 28px. Background: `#030705`.
- Scrolling marquee (`@keyframes tick`, 28s linear). Pauses on hover.
- Items: symbol (muted), price (mid), change (green/red/neutral).

### 3.4 Topbar

- Height: 50px. Background: `--bg-mid`.
- Left: page title in Fraunces italic 21px.
- Right: date in 11px mono + LIVE pill with animated dot (2s `hum`).

---

## 4. Dashboard Page

### 4.1 Stat Cards

Four-column grid. Each card: label (uppercase 9px), large value (22px), hint line with optional trend badge.

| Card | Value color | Trend badge | Hint text |
|------|------------|-------------|-----------|
| Portfolio Value | amber | ▲/▼ % vs. yesterday | "vs. yesterday" |
| Today's P&L | green (positive) / red (negative) | ▲/▼ % today | "today" |
| Unrealized P&L | green (positive) / red (negative) | ▲/▼ % total return | "total return" |
| Positions | `--text` (default) | none | "Across N sectors" (computed from position data) |

Trend badges are small inline pills (▲ green / ▼ red) with a tinted background (`rgba(78,221,138,.1)` / `rgba(240,120,104,.1)`), rendered inline before the hint text.

Cards glow on hover (`glow-ring`).

### 4.2 Watchlist Tiles

The primary new component. A CSS grid of tiles (`repeat(auto-fill, minmax(160px, 1fr))`), `align-items: start` so tiles don't stretch to match an expanded neighbor. `grid-auto-flow: dense` is applied so that when a tile expands to `span 2`, the browser back-fills any gap with remaining tiles rather than leaving an empty cell.

**Tile ordering:** sorted by daily % change descending — biggest winners first, losers last. The sort function must handle both ASCII hyphen (`-`) and Unicode minus (`−` U+2212) in the change string.

#### Tile price colors

The price value uses sentiment-specific colors (not the generic `--text`):

| Sentiment | Price color |
|-----------|-------------|
| Positive | `var(--amber)` (`#e8b45a`) |
| Negative | `rgba(240,120,104,.85)` |
| Neutral / flat | `rgba(232,180,90,.55)` (dimmed amber) |

#### At-rest tile structure

```
[3px left-edge sentiment strip]
┌──────────────────────────────┐
│ SYM               [+1.2%]   │
│ $188.32                      │
│ [sparkline SVG, 36px tall]   │
│ · headline one, up to 2 lines│
│ · headline two, up to 2 lines│
└──────────────────────────────┘
```

- **Sentiment strip:** 3px left border, `position: absolute`, flush with left/top/bottom of tile. Green (`rgba(78,221,138,.5)`) for positive, red (`rgba(240,120,104,.5)`) for negative, near-invisible (`rgba(200,200,200,.15)`) for flat. Tile needs `overflow: hidden` to respect border-radius.
- **Sparkline:** 1D intraday path, 36px tall SVG. Stroke color: green for positive, red for negative, `rgba(255,255,255,.18)` for neutral. Gradient fill below the line. Endpoint dot is not shown in the compact/at-rest state.
- **Compact news:** shown only when the ticker has **2 or more** headlines available from the API. Shows the first 2 headlines, each clamped to 2 lines (`-webkit-line-clamp: 2`). Hidden when tile is expanded.
- **Sparkline placeholder:** when no chart data is available, render a flat dashed line at `y=18` (vertical midpoint of the 36px SVG). No text label. The tile height matches data-bearing tiles.

#### Expanded tile (focused state)

Triggered by clicking anywhere on the tile body (not the ✕ button). Only one tile can be expanded at a time — clicking a new tile collapses any currently open tile. Clicking an already-expanded tile collapses it.

```
[3px strip]
┌───────────────────────────────────────────────┐
│ SYM                              [+1.2%]  [✕] │  ← row 1: symbol left, badge right
│ $188.32                                       │  ← row 2: price (✕ is position:absolute overlay)
│                                               │
│                            [1D] [5D] [3M]    │  ← timeframe toggle, right-aligned
│ [expanded sparkline SVG, 64px tall          ] │
│   · 3 faint dashed horizontal grid lines      │
│   · gradient fill                             │
│   · glowing endpoint dot at line tip          │
│ 9:30   11:00   1:00P   3:00P   Close          │  ← x-axis labels
│                                               │
│ ┌────────┐ ┌────────┐ ┌────────┐             │
│ │ Open   │ │ High   │ │ Low    │             │  ← fundamentals grid (3×2)
│ │ 186.10 │ │ 189.40 │ │ 185.80 │             │
│ ├────────┤ ├────────┤ ├────────┤             │
│ │Prev Cls│ │ Volume │ │[extra] │             │
│ │ 186.07 │ │  52.4M │ │ 2.89T  │             │
│ └────────┘ └────────┘ └────────┘             │
│ ─────────────────────────────────────────── │
│ RELATED NEWS                                  │
│ · Headline one                     Source · T │
│ · Headline two                     Source · T │
└───────────────────────────────────────────────┘
```

- **Grid column:** `grid-column: span 2`. With `grid-auto-flow: dense`, the grid will reflow remaining tiles to fill gaps. This is the intended behavior.
- **Glow:** `glow-ring-hi` border + `glowpulse` breathing animation (defined in section 2.2).
- **Timeframe toggle:** `1D | 5D | 3M` buttons, right-aligned above the chart. Active button: green tint background + `glow-sm`. Switching updates the sparkline path, endpoint dot position, and x-axis labels simultaneously.
- **X-axis labels by timeframe:**
  - 1D: `9:30 · 11:00 · 1:00P · 3:00P · Close`
  - 5D: `Mon · Tue · Wed · Thu · Fri`
  - 3M: `Jan · Feb · Mar 1 · Mar 8 · Now`
- **Endpoint dot:** filled circle `r=2.5` at the terminal `(x,y)` coordinate of the current path. Color matches the sparkline stroke. CSS `filter: drop-shadow(0 0 4px <color>)` for glow effect. Position updates on timeframe switch.
- **Expanded sparkline placeholder (no data):** renders the three grid lines + a flat dashed midline (`y=32` in the 64px SVG) + a centered "chart data unavailable" label in `--text-muted`. Timeframe toggle buttons are rendered but non-interactive (`pointer-events: none`, opacity reduced to 40%).
- **Fundamentals grid:** 3 columns × 2 rows. Fixed fields: Open, High, Low, Prev Close, Volume. Sixth cell is ticker-dependent:
  - ETFs (e.g. VOO, SPY): YTD return
  - Indices (e.g. SPX, NDX): Forward P/E
  - Individual equities: Market Cap if available, otherwise P/E trailing
  - Crypto: Market Cap
  - Fallback if no suitable metric is available: omit the cell (leave blank, grid renders 5 cells)
- **News section:** all available headlines rendered below the fundamentals, separated by a `1px solid var(--border)` divider. Each item: headline (10px, `--text-mid`) + source · time (8.5px, `--text-dim`). Hover nudges item 4px right.
- **Close button:** `✕`, `position: absolute`, top-right of tile. Collapses tile on click.
- **Default timeframe on open:** always 1D, regardless of previously selected timeframe.

---

## 5. Quotes Page

Apply the terminal dark theme (background, typography, color system, glow system) to the existing market data table. No structural changes to the layout or data. Interactive rows gain `glow-ring` on hover.

---

## 6. Portfolio Page

Apply the terminal dark theme to the existing layout: stat grid + Buffett quote panel. The Buffett quote panel retains its dynamic font sizing behavior (`fitQuoteText()`). Stat cards follow the same pattern as the Dashboard stat cards including trend badges where directional data is available.

---

## 7. Finance Page

Apply the terminal dark theme. No structural changes. Interactive elements gain hover glow consistent with the rest of the app.

---

## 8. Implementation Notes

### What is NOT in scope for v1

- **Live sparkline data:** sparklines render static placeholder paths on day one. Wiring real 1D/5D/3M candle data requires extending `MarketDataProvider` to support OHLC history (e.g. Finnhub `/stock/candle`). This is a distinct follow-on task.
- **Logo/brand mark redesign:** the "Benji" wordmark and tree emoji icon are acknowledged as needing improvement. Deferred to a separate design pass.
- **Mobile/responsive layout:** the design is desktop-first. No responsive breakpoints are defined in this spec.

### Single-file constraint

The frontend is a single HTML file served as a static resource from the JAR. All CSS and JavaScript must remain inline in `index.html`. No build step, no external assets beyond Google Fonts.

### Sparkline data seam

Sparkline paths should be generated by a dedicated function that accepts a data array and returns an SVG path string. This function should be easy to swap from returning a static placeholder path to computing from real candle data, without touching the rendering layer.
