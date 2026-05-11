# Learning Section Design

**Date:** 2026-05-11
**Status:** Approved design, pending implementation plan

## Goals

1. **Make finance approachable** — give financial beginners a warm, non-intimidating place to learn core concepts.
2. **Teach from first principles** — explain the durable ideas underneath money, investing, risk, and trading before introducing tactics.
3. **Provide a useful reference library** — make common terms, ELI5 explanations, and visual explainers easy to search and revisit.
4. **Create room for advanced topics later** — support future expansion into advanced options and trading strategies without crowding the first release.

## Product Direction

Learning is a new first-class section in Benji. It should feel like a path-first education hub, not a dense encyclopedia or course platform.

The default experience starts with a calm "Start Here" primer, then presents four learning paths:

1. **Money Basics** — income, expenses, budgeting, debt, emergency funds, and cash flow.
2. **Investing Basics** — stocks, ETFs, dividends, compounding, diversification, and ownership.
3. **Portfolio & Risk** — allocation, volatility, drawdowns, concentration, time horizon, and margin of safety.
4. **Trading & Options Intro** — orders, calls, puts, breakeven, payoff graphs, and the basic risks of trading instruments.

The page also includes a reference library for users who arrive with a specific question. The library should support search, glossary-style browsing, ELI5 explanations, and visual explainers.

## Educational Voice

The Learning section should be inspired by Warren Buffett and Benjamin Graham principles without imitating any person's exact writing style.

The voice should be:

- Plainspoken and patient.
- Grounded in first principles.
- Focused on ownership, cash flow, value, risk, time, and compounding.
- Careful about downside before upside.
- Skeptical of complexity for its own sake.
- Welcoming to beginners who do not yet speak finance jargon.

The first release should avoid presenting advanced trading as the center of the product. Trading and options should be framed as tools with tradeoffs, not shortcuts.

## Page Structure

Add a new Learning page to the existing single-page frontend. The desktop layout should be path-first:

1. **Start Here primer** at the top, introducing the first-principles framing.
2. **Learning path cards** for the four paths.
3. **Reference library** with search and filters.
4. **Concept detail panel** for the selected term or lesson.
5. **Visual explainer area** when the selected concept has a useful visual.

On smaller screens, these regions should stack in the same order. Search and concept detail should remain easy to use without horizontal scrolling or overlapping controls.

## Content Model

For the first release, content is local frontend data. No backend, database, CMS, account state, or progress tracking is required.

Each learning item should use a structured model similar to:

```js
{
  id: "what-is-a-stock",
  title: "What is a stock?",
  path: "investing-basics",
  level: "start-here",
  tags: ["stocks", "ownership", "business"],
  summary: "A stock is a small ownership stake in a business.",
  eli5: "Imagine a company is a pizza. A share of stock is one tiny slice of that pizza.",
  firstPrinciple: "A stock represents ownership in a real business, not just a blinking price on a screen.",
  whyItMatters: "When you buy stock, your result depends on the business, the price you paid, and time.",
  visualType: "ownership-slice",
  relatedTerms: ["intrinsic-value", "dividend", "market-price"]
}
```

Supported levels:

- `start-here`
- `beginner`
- `intermediate`
- `advanced-preview`

Advanced preview entries can exist in the reference library, but they should not dominate the first release.

## Initial Content Scope

The first release should seed enough content to make the page useful without pretending to be complete.

Suggested starter topics:

- Money Basics: income, expense, budget, emergency fund, debt, interest, cash flow.
- Investing Basics: stock, bond, ETF, index fund, dividend, compounding, diversification, intrinsic value.
- Portfolio & Risk: allocation, volatility, drawdown, concentration risk, time horizon, margin of safety.
- Trading & Options Intro: market order, limit order, bid/ask spread, call option, put option, strike price, expiration, breakeven.

## Visual Explainers

Interactive visuals should be small teaching tools, not full analytics dashboards. Each visual should answer one beginner question clearly.

First-release visual candidates:

1. **Compounding curve** — shows how time changes long-term outcomes.
2. **Cash-flow waterfall** — income minus needs, debt, savings, and investing.
3. **Diversification grid** — shows the difference between owning one company and many.
4. **Risk/reward scale** — compares savings, bonds, broad ETFs, individual stocks, and options.
5. **Allocation pie or treemap** — connects portfolio construction to Benji's existing portfolio visuals.
6. **Option payoff graph** — simple call and put payoff at expiration, with breakeven marked.

Each visual should be paired with:

- ELI5 explanation.
- First-principle explanation.
- Why-it-matters explanation.

## Technical Approach

Build v1 entirely inside:

`apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html`

Required frontend changes:

1. Add a `Learning` sidebar navigation item.
2. Add a `page-learning` section.
3. Add local JavaScript arrays for learning paths and learning items.
4. Add render functions for:
   - Learning home.
   - Path filtering.
   - Search/filtering.
   - Selected concept detail.
   - Visual explainers.
5. Add responsive CSS for the Learning layout and cards.
6. Add Learning to the page title map and navigation switching.

The implementation should reuse the current app style: dark terminal-inspired surface, restrained green accents, compact cards, monospace UI text, and existing button/input patterns.

No backend endpoints are needed for v1.

## Data Flow

1. User opens the Learning page from navigation.
2. Frontend renders the Start Here primer, path cards, and reference search from local data.
3. User selects a path, searches a term, or opens a concept card.
4. Frontend filters local items and renders the selected concept.
5. If the concept has a `visualType`, the matching visual renderer draws or updates the explainer.

This keeps the feature deterministic, offline-friendly, and easy to revise.

## Error And Empty States

The Learning section should handle:

- Empty search result: show a short "No matching concept yet" message and suggest clearing filters.
- Missing visual renderer: show the text explanation without an error.
- Unknown related term: omit the broken link instead of rendering a dead control.
- Small viewport: stack layout vertically and keep search usable.

## Testing And Verification

Implementation verification should include:

1. Navigation opens Learning and updates active nav state and page title.
2. Search finds seeded terms across title, summary, tags, and glossary text.
3. Path filters show only matching items.
4. Selecting a concept updates the detail panel.
5. Visual explainers render nonblank and do not overlap text.
6. Mobile viewport stacks content cleanly.
7. Existing Dashboard, Quotes, Portfolio, Finance, Journal, and Import navigation still works.

## Out Of Scope For V1

- Backend content APIs.
- Database persistence.
- User progress tracking.
- Quizzes, certificates, or course completion state.
- AI-generated lessons.
- Admin authoring tools.
- A complete advanced-options curriculum.

## Future Expansion

The content model should make later expansion straightforward:

- Add richer advanced tracks such as options strategies, portfolio hedging, macro cycles, and factor investing.
- Move local content into backend-authored content if the library grows large.
- Add saved progress once the paths become course-like.
- Add contextual "learn this metric" links from Portfolio, Finance, Quotes, and Journal pages into Learning concepts.
- Add more visual simulations, especially for options risk graphs and scenario analysis.
