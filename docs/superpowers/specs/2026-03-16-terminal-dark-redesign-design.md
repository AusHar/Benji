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

A `glowpulse` keyframe animation breathes the expanded tile glow between `glow-ring-hi` and a slightly brighter peak on a 3s loop.

A `hum` keyframe animates the live indicator dot and connection badge dot between a tight and wide glow radius on a 2–2.5s loop.

### 2.3 Typography

| Use | Font | Weight | Style |
|-----|------|--------|-------|
| Brand name ("Benji"), page titles | Fraunces | 300 | italic, optical-sizing: auto |
| All other text — labels, values, nav, data | IBM Plex Mono | 300 / 400 / 500 | normal |

Font sizes follow a strict scale: 7.5px (nav groups), 8–9px (labels/hints), 10–11px (secondary data), 13px (base body), 18px (tile prices), 21px (topbar title / brand), 22px (stat card values).

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
- **Behavior:** Auto-collapses to icon-only after 4 seconds of mouse-off. Re-expands immediately on `mouseenter`. Uses CSS `width` transition (cubic-bezier `.4,0,.2,1`, 300ms).
- **Nav items:** Dashboard, Quotes, Portfolio, Finance. Active item highlighted with green tint + `glow-sm`.
- **Footer:** "Connected" badge with animated dot. Dot uses `hum` animation.

### 3.3 Ticker Bar

- Height: 28px. Background: `#030705`.
- Scrolling marquee (`@keyframes tick`, 28s linear). Pauses on hover.
- Items: symbol (muted), price (mid), change (green/red/neutral).

### 3.4 Topbar

- Height: 50px. Background: `--bg-mid`.
- Left: page title in Fraunces italic 21px.
- Right: date in 11px mono + LIVE pill with animated dot.

---

## 4. Dashboard Page

### 4.1 Stat Cards

Four-column grid. Each card: label (uppercase 9px), large value (22px), trend hint.

| Card | Value color | Trend badge |
|------|------------|-------------|
| Portfolio Value | amber | ▲/▼ % vs. yesterday |
| Today's P&L | green (if positive) | ▲/▼ % today |
| Unrealized P&L | green (if positive) | ▲/▼ % total return |
| Positions | default | none (count, not directional) |

Trend badges are small inline pills (▲ green / ▼ red) with a tinted background, rendered inside the hint line.

Cards glow on hover (`glow-ring`).

### 4.2 Watchlist Tiles

The primary new component. A CSS grid of tiles (`repeat(auto-fill, minmax(160px, 1fr))`), `align-items: start` so tiles don't stretch to match an expanded neighbor.

**Tile ordering:** sorted by daily % change descending — biggest winners first, losers last.

#### At-rest tile structure

```
[3px left-edge sentiment strip]
[sym]              [+1.2% badge]
[$188.32          ]
[sparkline SVG (36px)]
[compact news — 2 headlines, clamped to 2 lines each]
```

- **Sentiment strip:** 3px left border flush with tile edge. Green (`rgba(78,221,138,.5)`) for positive, red (`rgba(240,120,104,.5)`) for negative, near-invisible white for flat.
- **Sparkline:** 1D intraday path, 36px tall. Color: green for positive, red for negative, faint white for neutral. Gradient fill below the line.
- **Compact news:** shown only when the ticker has more than 1 available headline. 2 headlines max, each `-webkit-line-clamp: 2`. Hidden when tile is expanded.
- **Sparkline placeholder:** when no chart data is available, render a flat dashed line at the vertical midpoint (`y=18`) instead of a real path. No additional label needed.

#### Expanded tile (focused state)

Triggered by clicking anywhere on the tile. Only one tile can be expanded at a time.

```
[3px strip]  [sym]  [price]  [chg badge]          [✕]
[timeframe toggle: 1D | 5D | 3M              ]
[expanded sparkline SVG (64px)               ]
  - 3 faint dashed horizontal grid lines (y=16, 32, 48)
  - gradient fill under the line
  - glowing endpoint dot at tip of line
[x-axis labels (5 labels, space-between)     ]
[fundamentals grid (3×2 cells)               ]
[─────────────────────────────────────────── ]
[Related News (full headlines, all items)    ]
```

- **Grid column:** `span 2` — tile doubles in width.
- **Glow:** `glow-ring-hi` border + `glowpulse` breathing animation.
- **Timeframe toggle:** `1D | 5D | 3M` buttons. Active button has green tint + `glow-sm`. Switching updates the sparkline path, endpoint dot position, and x-axis labels.
- **X-axis labels by timeframe:**
  - 1D: `9:30 · 11:00 · 1:00P · 3:00P · Close`
  - 5D: `Mon · Tue · Wed · Thu · Fri`
  - 3M: `Jan · Feb · Mar 1 · Mar 8 · Now`
- **Endpoint dot:** small filled circle (`r=2.5`) at the terminal coordinate of the current sparkline path. Color matches the line. Rendered with a CSS `drop-shadow` filter for glow. Updates on timeframe switch.
- **Expanded sparkline placeholder:** when no chart data exists, renders the three grid lines + a flat dashed midline + a centered "chart data unavailable" label. Timeframe toggle remains rendered but inert.
- **Fundamentals grid:** 3 columns × 2 rows of stat cells (Open, High, Low, Prev Close, Volume, and one ticker-specific metric). Each cell: label (uppercase 7.5px), value (12px mono). Glows on hover.
- **News section:** all available headlines, each with a source + timestamp line. Items have a subtle left-indent hover animation. Separated from fundamentals by a divider line.
- **Close:** ✕ button top-right. Clicking collapses tile back to at-rest state.
- **Default timeframe on open:** always 1D.

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
