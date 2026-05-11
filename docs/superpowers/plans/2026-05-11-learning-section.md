# Learning Section Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a first-principles Learning section to Benji with learning paths, searchable reference content, ELI5 explanations, and small visual explainers.

**Architecture:** Implement v1 entirely in the existing static single-page frontend. Keep content in local JavaScript data, render the page from deterministic functions, and draw interactive explainers with inline SVG/canvas-style DOM where possible to avoid adding dependencies.

**Tech Stack:** Spring Boot static resource, plain HTML/CSS/JavaScript, existing Benji CSS variables and UI patterns, browser smoke verification.

---

## File Structure

- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html`
  - Add Learning navigation entries.
  - Add `#page-learning`.
  - Add Learning CSS.
  - Add local content data.
  - Add render, filter, selection, and visual explainer functions.
  - Wire Learning into the existing `go(page)` router.
- No backend files are required.
- No database migration is required.

## Task 1: Navigation And Page Shell

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html`

- [ ] **Step 1: Verify Learning does not exist yet**

Run:

```bash
rg -n "Learning|page-learning|renderLearning" apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html
```

Expected: no matches.

- [ ] **Step 2: Add desktop sidebar navigation**

In the sidebar, insert a new group after the Portfolio nav item and before the `Personal` group:

```html
      <div class="nav-group">Education</div>
      <div class="nav-item" data-page="learning">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
          <path d="M2 2.5h4.4a1.6 1.6 0 0 1 1.6 1.6v7.4a1.6 1.6 0 0 0-1.6-1.6H2z"/>
          <path d="M12 2.5H7.6A1.6 1.6 0 0 0 6 4.1v7.4a1.6 1.6 0 0 1 1.6-1.6H12z"/>
        </svg>
        <span class="nav-label">Learning</span>
      </div>
```

- [ ] **Step 3: Add the Learning page shell**

After the Portfolio page and before the Finance page, add:

```html
      <!-- Learning -->
      <div class="page" id="page-learning">
        <div id="learning-root">
          <div class="state-box"><span class="spinner"></span></div>
        </div>
      </div>
```

- [ ] **Step 4: Add Learning to the mobile bottom tab bar**

Insert this button after Portfolio and before Finance:

```html
  <button data-page="learning" onclick="go('learning')">
    <svg width="18" height="18" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
      <path d="M2 2.5h4.4a1.6 1.6 0 0 1 1.6 1.6v7.4a1.6 1.6 0 0 0-1.6-1.6H2z"/>
      <path d="M12 2.5H7.6A1.6 1.6 0 0 0 6 4.1v7.4a1.6 1.6 0 0 1 1.6-1.6H12z"/>
    </svg>
    Learning
  </button>
```

- [ ] **Step 5: Wire the router**

Change the title map from:

```js
const TITLES = { dashboard: 'Dashboard', quotes: 'Quotes', portfolio: 'Portfolio', finance: 'Finance', journal: 'Journal', import: 'Import' };
```

to:

```js
const TITLES = {
  dashboard: 'Dashboard',
  quotes: 'Quotes',
  portfolio: 'Portfolio',
  learning: 'Learning',
  finance: 'Finance',
  journal: 'Journal',
  import: 'Import'
};
```

Add this line inside `go(page)` after the Portfolio line:

```js
    if (page === 'learning')  renderLearning();
```

- [ ] **Step 6: Verify shell wiring**

Run:

```bash
rg -n "data-page=\"learning\"|page-learning|learning: 'Learning'|renderLearning\\(\\)" apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html
```

Expected: desktop nav, mobile nav, page shell, title map, and router hook all appear.

- [ ] **Step 7: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html
git commit -m "feat: add learning page shell"
```

## Task 2: Learning CSS

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html`

- [ ] **Step 1: Add desktop Learning styles**

Add this CSS block before the existing `/* ── Journal */` section:

```css
    /* ── Learning ───────────────────────────────────────── */
    .learn-hero {
      display: grid;
      grid-template-columns: minmax(0, 1.2fr) minmax(280px, .8fr);
      gap: 16px;
      margin-bottom: 18px;
    }
    .learn-primer {
      background: linear-gradient(135deg, rgba(78,221,138,.12), rgba(78,221,138,.035));
      border: 1px solid var(--border-mid);
      border-radius: 8px;
      padding: 20px;
      box-shadow: inset 0 1px 0 rgba(78,221,138,.08);
    }
    .learn-kicker {
      font-size: 8px;
      letter-spacing: 1.2px;
      text-transform: uppercase;
      color: var(--green-dim);
      margin-bottom: 8px;
    }
    .learn-title {
      font-family: var(--serif);
      font-style: italic;
      font-size: 25px;
      font-weight: 300;
      line-height: 1.15;
      color: var(--text);
      margin-bottom: 10px;
    }
    .learn-copy {
      color: var(--text-mid);
      font-size: 12px;
      line-height: 1.7;
      max-width: 760px;
    }
    .learn-principles {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 8px;
      margin-top: 16px;
    }
    .learn-principle {
      border: 1px solid rgba(78,221,138,.12);
      border-radius: 6px;
      padding: 10px;
      background: rgba(5,10,7,.28);
    }
    .learn-principle b {
      display: block;
      color: var(--text);
      font-size: 10px;
      margin-bottom: 4px;
    }
    .learn-principle span {
      color: var(--text-dim);
      font-size: 10px;
      line-height: 1.5;
    }
    .learn-search-panel,
    .learn-detail,
    .learn-visual,
    .learn-path-card,
    .learn-concept-card {
      background: var(--bg-card);
      border: 1px solid var(--border);
      border-radius: 8px;
      box-shadow: inset 0 1px 0 rgba(78,221,138,.06);
    }
    .learn-search-panel {
      padding: 14px;
    }
    .learn-search-panel .field {
      width: 100%;
      margin-bottom: 10px;
    }
    .learn-filter-row {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .learn-chip {
      border: 1px solid var(--border);
      border-radius: 999px;
      background: rgba(78,221,138,.04);
      color: var(--text-dim);
      cursor: pointer;
      font-family: var(--mono);
      font-size: 9px;
      padding: 5px 9px;
    }
    .learn-chip.active,
    .learn-chip:hover {
      border-color: var(--border-mid);
      color: var(--green);
      box-shadow: var(--glow-sm);
    }
    .learn-path-grid {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 12px;
      margin-bottom: 18px;
    }
    .learn-path-card {
      cursor: pointer;
      min-height: 132px;
      padding: 14px;
      transition: border-color .18s, background .18s, box-shadow .5s;
    }
    .learn-path-card:hover,
    .learn-path-card.active {
      background: var(--bg-card-hi);
      border-color: var(--border-mid);
      box-shadow: var(--glow-ring);
    }
    .learn-path-name {
      color: var(--text);
      font-size: 13px;
      margin-bottom: 7px;
    }
    .learn-path-summary {
      color: var(--text-dim);
      font-size: 10.5px;
      line-height: 1.55;
    }
    .learn-path-count {
      color: var(--green-dim);
      font-size: 9px;
      margin-top: 12px;
    }
    .learn-body {
      display: grid;
      grid-template-columns: minmax(260px, .9fr) minmax(0, 1.1fr);
      gap: 16px;
    }
    .learn-concept-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .learn-concept-card {
      cursor: pointer;
      padding: 12px;
      text-align: left;
      width: 100%;
      font-family: var(--mono);
    }
    .learn-concept-card:hover,
    .learn-concept-card.active {
      border-color: var(--border-mid);
      background: var(--bg-card-hi);
    }
    .learn-concept-title {
      color: var(--text);
      font-size: 12px;
      margin-bottom: 5px;
    }
    .learn-concept-summary {
      color: var(--text-dim);
      font-size: 10.5px;
      line-height: 1.55;
    }
    .learn-level {
      color: var(--amber);
      font-size: 8px;
      letter-spacing: .8px;
      text-transform: uppercase;
      margin-bottom: 6px;
    }
    .learn-detail {
      padding: 18px;
    }
    .learn-detail h2 {
      font-family: var(--serif);
      font-style: italic;
      font-weight: 300;
      font-size: 24px;
      margin-bottom: 8px;
    }
    .learn-explain-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 10px;
      margin: 16px 0;
    }
    .learn-explain {
      border: 1px solid rgba(78,221,138,.1);
      border-radius: 6px;
      padding: 12px;
      background: rgba(5,10,7,.22);
    }
    .learn-explain-label {
      color: var(--green-dim);
      font-size: 8px;
      letter-spacing: .9px;
      text-transform: uppercase;
      margin-bottom: 7px;
    }
    .learn-explain p {
      color: var(--text-mid);
      font-size: 11px;
      line-height: 1.6;
    }
    .learn-visual {
      padding: 14px;
      margin-top: 12px;
      min-height: 190px;
    }
    .learn-visual svg {
      display: block;
      width: 100%;
      height: auto;
      overflow: visible;
    }
    .learn-empty {
      color: var(--text-dim);
      font-size: 12px;
      line-height: 1.7;
      padding: 18px;
      border: 1px dashed var(--border-mid);
      border-radius: 8px;
    }
```

- [ ] **Step 2: Add mobile Learning styles**

Inside the existing mobile media query, add:

```css
      .learn-hero,
      .learn-body {
        grid-template-columns: 1fr;
      }
      .learn-path-grid {
        grid-template-columns: 1fr;
      }
      .learn-principles,
      .learn-explain-grid {
        grid-template-columns: 1fr;
      }
      .learn-title {
        font-size: 21px;
      }
      .learn-detail h2 {
        font-size: 21px;
      }
```

- [ ] **Step 3: Verify CSS selectors exist**

Run:

```bash
rg -n "\\.learn-hero|\\.learn-path-grid|\\.learn-body|\\.learn-visual|\\.learn-explain-grid" apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html
```

Expected: each selector appears at least once.

- [ ] **Step 4: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html
git commit -m "feat: style learning section"
```

## Task 3: Content Data And State

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html`

- [ ] **Step 1: Add local Learning data**

Place this block after `statCard` and `empty`, before the page-specific API functions:

```js
  const LEARNING_PATHS = [
    {
      id: 'money-basics',
      name: 'Money Basics',
      summary: 'Cash flow before complexity: income, spending, debt, savings, and emergency reserves.',
      principle: 'A dollar can only do one job at a time.'
    },
    {
      id: 'investing-basics',
      name: 'Investing Basics',
      summary: 'Ownership, value, compounding, dividends, funds, and the difference between price and business.',
      principle: 'A stock is a piece of a business, not just a ticker.'
    },
    {
      id: 'portfolio-risk',
      name: 'Portfolio & Risk',
      summary: 'Allocation, volatility, concentration, drawdowns, time horizon, and margin of safety.',
      principle: 'Survival comes before optimization.'
    },
    {
      id: 'trading-options-intro',
      name: 'Trading & Options Intro',
      summary: 'Orders, bid/ask spreads, calls, puts, breakeven, expiration, and payoff diagrams.',
      principle: 'Tools are useful only when you understand their failure modes.'
    }
  ];

  const LEARNING_ITEMS = [
    {
      id: 'cash-flow',
      title: 'Cash Flow',
      path: 'money-basics',
      level: 'start-here',
      tags: ['income', 'expenses', 'budget'],
      summary: 'Cash flow is the money coming in minus the money going out.',
      eli5: 'If allowance comes in and snacks go out, what is left is your cash flow.',
      firstPrinciple: 'Before money can compound, it has to remain under your control.',
      whyItMatters: 'Positive cash flow gives you choices: save, invest, reduce debt, or build a cushion.',
      visualType: 'cash-flow-waterfall',
      relatedTerms: ['emergency-fund', 'debt', 'compound-interest']
    },
    {
      id: 'emergency-fund',
      title: 'Emergency Fund',
      path: 'money-basics',
      level: 'beginner',
      tags: ['savings', 'risk', 'cash'],
      summary: 'An emergency fund is money kept available for surprise expenses or income gaps.',
      eli5: 'It is the umbrella you carry before it rains.',
      firstPrinciple: 'Liquidity protects you from being forced to sell long-term assets at the wrong time.',
      whyItMatters: 'A cash cushion can prevent debt spirals and panic decisions.',
      visualType: 'risk-reward-scale',
      relatedTerms: ['cash-flow', 'debt']
    },
    {
      id: 'debt',
      title: 'Debt',
      path: 'money-basics',
      level: 'beginner',
      tags: ['interest', 'borrowing', 'cash flow'],
      summary: 'Debt is borrowed money that must be repaid, usually with interest.',
      eli5: 'Debt means future-you has promised to pay for something present-you used.',
      firstPrinciple: 'Interest can work for you or against you.',
      whyItMatters: 'High-interest debt can outrun your investments and weaken cash flow.',
      visualType: 'cash-flow-waterfall',
      relatedTerms: ['cash-flow', 'compound-interest']
    },
    {
      id: 'stock',
      title: 'Stock',
      path: 'investing-basics',
      level: 'start-here',
      tags: ['ownership', 'business', 'shares'],
      summary: 'A stock is a small ownership stake in a business.',
      eli5: 'If a company were a pizza, a share of stock would be one tiny slice.',
      firstPrinciple: 'A share is ownership in a real business, not just a blinking price.',
      whyItMatters: 'Long-term returns depend on business results, the price paid, and time.',
      visualType: 'ownership-slice',
      relatedTerms: ['intrinsic-value', 'dividend', 'market-price']
    },
    {
      id: 'etf',
      title: 'ETF',
      path: 'investing-basics',
      level: 'beginner',
      tags: ['funds', 'diversification', 'index'],
      summary: 'An ETF is a basket of investments that trades like a stock.',
      eli5: 'Instead of buying one apple, you buy a fruit basket.',
      firstPrinciple: 'Diversification reduces dependence on one outcome.',
      whyItMatters: 'Broad ETFs can make ownership simpler and reduce single-company risk.',
      visualType: 'diversification-grid',
      relatedTerms: ['stock', 'diversification', 'allocation']
    },
    {
      id: 'compound-interest',
      title: 'Compounding',
      path: 'investing-basics',
      level: 'start-here',
      tags: ['time', 'growth', 'interest'],
      summary: 'Compounding is growth earning more growth over time.',
      eli5: 'It is a snowball that gets bigger as it rolls because new snow sticks to old snow.',
      firstPrinciple: 'Time can turn modest returns into meaningful outcomes when gains stay invested.',
      whyItMatters: 'Starting earlier can matter more than finding the perfect investment.',
      visualType: 'compounding-curve',
      relatedTerms: ['dividend', 'time-horizon']
    },
    {
      id: 'intrinsic-value',
      title: 'Intrinsic Value',
      path: 'investing-basics',
      level: 'intermediate',
      tags: ['value', 'business', 'price'],
      summary: 'Intrinsic value is what an asset is reasonably worth based on its future cash flows.',
      eli5: 'It is what the lemonade stand is worth because of the lemonade it can sell, not because someone shouts a price.',
      firstPrinciple: 'Price is what you pay; value is what you receive.',
      whyItMatters: 'Investors need a way to judge whether the market price is attractive or dangerous.',
      visualType: 'risk-reward-scale',
      relatedTerms: ['stock', 'margin-of-safety', 'market-price']
    },
    {
      id: 'allocation',
      title: 'Allocation',
      path: 'portfolio-risk',
      level: 'beginner',
      tags: ['portfolio', 'risk', 'diversification'],
      summary: 'Allocation is how your money is divided across assets.',
      eli5: 'It is how many eggs you put in each basket.',
      firstPrinciple: 'What you own, and how much of it you own, drives most portfolio risk.',
      whyItMatters: 'Allocation helps align investments with goals, time horizon, and risk tolerance.',
      visualType: 'allocation-pie',
      relatedTerms: ['diversification', 'volatility', 'time-horizon']
    },
    {
      id: 'margin-of-safety',
      title: 'Margin of Safety',
      path: 'portfolio-risk',
      level: 'intermediate',
      tags: ['risk', 'value', 'downside'],
      summary: 'Margin of safety means leaving room for being wrong.',
      eli5: 'If a bridge must hold 10 tons, you would rather it be built for 20.',
      firstPrinciple: 'The future is uncertain, so good decisions include a buffer.',
      whyItMatters: 'A margin of safety can reduce permanent loss when assumptions disappoint.',
      visualType: 'risk-reward-scale',
      relatedTerms: ['intrinsic-value', 'drawdown', 'volatility']
    },
    {
      id: 'volatility',
      title: 'Volatility',
      path: 'portfolio-risk',
      level: 'beginner',
      tags: ['risk', 'price movement', 'portfolio'],
      summary: 'Volatility is how much an investment price moves around.',
      eli5: 'It is the bumpiness of the ride.',
      firstPrinciple: 'Price movement is not the same thing as permanent loss, but it can pressure behavior.',
      whyItMatters: 'Volatility can create opportunity or panic depending on your time horizon and cash needs.',
      visualType: 'risk-reward-scale',
      relatedTerms: ['drawdown', 'time-horizon', 'allocation']
    },
    {
      id: 'call-option',
      title: 'Call Option',
      path: 'trading-options-intro',
      level: 'advanced-preview',
      tags: ['options', 'calls', 'breakeven'],
      summary: 'A call option gives the buyer the right, not the obligation, to buy an asset at a set price before expiration.',
      eli5: 'It is like paying for a coupon that lets you buy something at a fixed price later.',
      firstPrinciple: 'Options separate exposure from ownership and add time limits.',
      whyItMatters: 'Calls can magnify gains and losses because the premium can expire worthless.',
      visualType: 'option-payoff',
      relatedTerms: ['put-option', 'strike-price', 'expiration']
    },
    {
      id: 'put-option',
      title: 'Put Option',
      path: 'trading-options-intro',
      level: 'advanced-preview',
      tags: ['options', 'puts', 'breakeven'],
      summary: 'A put option gives the buyer the right, not the obligation, to sell an asset at a set price before expiration.',
      eli5: 'It is like paying for price insurance on something you own or want to bet against.',
      firstPrinciple: 'A put can transfer downside exposure for a known upfront premium.',
      whyItMatters: 'Puts can hedge risk or speculate on declines, but timing and premium matter.',
      visualType: 'option-payoff',
      relatedTerms: ['call-option', 'strike-price', 'expiration']
    },
    {
      id: 'bid-ask-spread',
      title: 'Bid/Ask Spread',
      path: 'trading-options-intro',
      level: 'beginner',
      tags: ['orders', 'trading', 'liquidity'],
      summary: 'The bid/ask spread is the gap between what buyers offer and sellers request.',
      eli5: 'One person wants to pay $9, another wants $10, and the $1 gap is the spread.',
      firstPrinciple: 'Every trade has friction, even when commissions look free.',
      whyItMatters: 'Wide spreads can quietly make trades more expensive, especially in options.',
      visualType: 'risk-reward-scale',
      relatedTerms: ['market-order', 'limit-order']
    }
  ];

  const learningState = {
    path: 'all',
    query: '',
    selectedId: 'cash-flow'
  };
```

- [ ] **Step 2: Verify data constants exist**

Run:

```bash
rg -n "const LEARNING_PATHS|const LEARNING_ITEMS|const learningState|cash-flow|call-option" apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html
```

Expected: all constants and representative items appear.

- [ ] **Step 3: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html
git commit -m "feat: add learning content data"
```

## Task 4: Rendering, Search, And Selection

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html`

- [ ] **Step 1: Add utility helpers and main renderer**

Add this JavaScript after the Learning data:

```js
  function learningPathName(pathId) {
    return LEARNING_PATHS.find(p => p.id === pathId)?.name || 'Learning';
  }

  function learningLevelLabel(level) {
    return String(level || '')
      .split('-')
      .map(part => part.charAt(0).toUpperCase() + part.slice(1))
      .join(' ');
  }

  function learningFilteredItems() {
    const q = learningState.query.trim().toLowerCase();
    return LEARNING_ITEMS.filter(item => {
      const pathMatch = learningState.path === 'all' || item.path === learningState.path;
      if (!pathMatch) return false;
      if (!q) return true;
      const haystack = [
        item.title,
        item.summary,
        item.eli5,
        item.firstPrinciple,
        item.whyItMatters,
        item.path,
        item.level,
        ...(item.tags || [])
      ].join(' ').toLowerCase();
      return haystack.includes(q);
    });
  }

  function renderLearning() {
    const root = document.getElementById('learning-root');
    if (!root) return;

    const filtered = learningFilteredItems();
    if (!filtered.some(item => item.id === learningState.selectedId)) {
      learningState.selectedId = filtered[0]?.id || LEARNING_ITEMS[0]?.id;
    }
    const selected = LEARNING_ITEMS.find(item => item.id === learningState.selectedId) || LEARNING_ITEMS[0];

    root.innerHTML = `
      <div class="learn-hero">
        <section class="learn-primer">
          <div class="learn-kicker">Start Here</div>
          <div class="learn-title">Finance from first principles</div>
          <p class="learn-copy">
            Start with durable ideas before tactics: cash flow, ownership, value, risk, time, and incentives.
            The goal is not to memorize jargon. The goal is to understand what is really happening underneath the numbers.
          </p>
          <div class="learn-principles">
            <div class="learn-principle"><b>Understand what you own</b><span>Assets are claims on future value, cash flow, or usefulness.</span></div>
            <div class="learn-principle"><b>Protect the downside</b><span>Good decisions leave room for being wrong.</span></div>
            <div class="learn-principle"><b>Let time work</b><span>Compounding rewards patience more than constant motion.</span></div>
          </div>
        </section>
        <aside class="learn-search-panel">
          <div class="section-title" style="margin-bottom:10px">Reference Library</div>
          <input class="field" id="learningSearch" type="search" placeholder="Search stocks, cash flow, options..." value="${escapeHtml(learningState.query)}" autocomplete="off">
          <div class="learn-filter-row">
            ${renderLearningChip('all', 'All')}
            ${LEARNING_PATHS.map(path => renderLearningChip(path.id, path.name)).join('')}
          </div>
        </aside>
      </div>
      <div class="learn-path-grid">
        ${LEARNING_PATHS.map(renderLearningPathCard).join('')}
      </div>
      <div class="learn-body">
        <section>
          <div class="section-head">
            <span class="section-title">${filtered.length} Concept${filtered.length === 1 ? '' : 's'}</span>
            <button class="section-action" onclick="clearLearningFilters()">Clear</button>
          </div>
          <div class="learn-concept-list">
            ${filtered.length ? filtered.map(renderLearningConceptCard).join('') : '<div class="learn-empty">No matching concept yet. Clear filters or try a broader search.</div>'}
          </div>
        </section>
        <section>
          ${selected ? renderLearningDetail(selected) : '<div class="learn-empty">Choose a concept to start learning.</div>'}
        </section>
      </div>
    `;

    document.getElementById('learningSearch')?.addEventListener('input', e => {
      const cursor = e.target.selectionStart;
      learningState.query = e.target.value;
      renderLearning();
      const search = document.getElementById('learningSearch');
      search?.focus();
      search?.setSelectionRange(cursor, cursor);
    });

    if (selected?.visualType) {
      renderLearningVisual(selected);
    }
  }
```

- [ ] **Step 2: Add HTML rendering helpers**

Add these functions after `renderLearning()`:

```js
  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function renderLearningChip(pathId, label) {
    const active = learningState.path === pathId ? ' active' : '';
    return `<button class="learn-chip${active}" onclick="selectLearningPath('${pathId}')">${escapeHtml(label)}</button>`;
  }

  function renderLearningPathCard(path) {
    const count = LEARNING_ITEMS.filter(item => item.path === path.id).length;
    const active = learningState.path === path.id ? ' active' : '';
    return `<button class="learn-path-card${active}" onclick="selectLearningPath('${path.id}')">
      <div class="learn-path-name">${escapeHtml(path.name)}</div>
      <div class="learn-path-summary">${escapeHtml(path.summary)}</div>
      <div class="learn-path-count">${count} concept${count === 1 ? '' : 's'} · ${escapeHtml(path.principle)}</div>
    </button>`;
  }

  function renderLearningConceptCard(item) {
    const active = learningState.selectedId === item.id ? ' active' : '';
    return `<button class="learn-concept-card${active}" onclick="selectLearningConcept('${item.id}')">
      <div class="learn-level">${escapeHtml(learningPathName(item.path))} · ${escapeHtml(learningLevelLabel(item.level))}</div>
      <div class="learn-concept-title">${escapeHtml(item.title)}</div>
      <div class="learn-concept-summary">${escapeHtml(item.summary)}</div>
    </button>`;
  }

  function renderLearningDetail(item) {
    const related = (item.relatedTerms || [])
      .map(id => LEARNING_ITEMS.find(candidate => candidate.id === id))
      .filter(Boolean);
    return `<article class="learn-detail">
      <div class="learn-level">${escapeHtml(learningPathName(item.path))} · ${escapeHtml(learningLevelLabel(item.level))}</div>
      <h2>${escapeHtml(item.title)}</h2>
      <p class="learn-copy">${escapeHtml(item.summary)}</p>
      <div class="learn-explain-grid">
        <div class="learn-explain">
          <div class="learn-explain-label">ELI5</div>
          <p>${escapeHtml(item.eli5)}</p>
        </div>
        <div class="learn-explain">
          <div class="learn-explain-label">First Principle</div>
          <p>${escapeHtml(item.firstPrinciple)}</p>
        </div>
        <div class="learn-explain">
          <div class="learn-explain-label">Why It Matters</div>
          <p>${escapeHtml(item.whyItMatters)}</p>
        </div>
      </div>
      ${item.visualType ? `<div class="learn-visual" id="learningVisual" aria-label="${escapeHtml(item.title)} visual explainer"></div>` : ''}
      ${related.length ? `<div class="section-head" style="margin-top:16px;margin-bottom:8px"><span class="section-title">Related</span></div>
        <div class="learn-filter-row">${related.map(term => `<button class="learn-chip" onclick="selectLearningConcept('${term.id}')">${escapeHtml(term.title)}</button>`).join('')}</div>` : ''}
    </article>`;
  }
```

- [ ] **Step 3: Add interaction functions**

Add these functions after the rendering helpers:

```js
  function selectLearningPath(pathId) {
    learningState.path = pathId;
    learningState.query = '';
    const first = learningFilteredItems()[0];
    if (first) learningState.selectedId = first.id;
    renderLearning();
  }

  function selectLearningConcept(id) {
    learningState.selectedId = id;
    renderLearning();
  }

  function clearLearningFilters() {
    learningState.path = 'all';
    learningState.query = '';
    learningState.selectedId = 'cash-flow';
    renderLearning();
  }
```

- [ ] **Step 4: Verify render functions exist**

Run:

```bash
rg -n "function renderLearning|function learningFilteredItems|function selectLearningPath|function clearLearningFilters|function escapeHtml" apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html
```

Expected: all functions appear once.

- [ ] **Step 5: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html
git commit -m "feat: render learning library"
```

## Task 5: Visual Explainers

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html`

- [ ] **Step 1: Add visual renderer dispatcher**

Add this function after the Learning interaction functions:

```js
  function renderLearningVisual(item) {
    const el = document.getElementById('learningVisual');
    if (!el) return;
    const renderers = {
      'compounding-curve': learningVisualCompounding,
      'cash-flow-waterfall': learningVisualCashFlow,
      'diversification-grid': learningVisualDiversification,
      'risk-reward-scale': learningVisualRiskReward,
      'allocation-pie': learningVisualAllocation,
      'option-payoff': learningVisualOptionPayoff,
      'ownership-slice': learningVisualOwnership
    };
    const renderer = renderers[item.visualType];
    el.innerHTML = renderer ? renderer(item) : `<div class="learn-empty">This concept is best learned through the explanation above.</div>`;
  }
```

- [ ] **Step 2: Add SVG helper**

Add:

```js
  function svgText(x, y, text, color = 'var(--text-dim)', size = 10, anchor = 'middle') {
    return `<text x="${x}" y="${y}" fill="${color}" font-size="${size}" text-anchor="${anchor}" font-family="IBM Plex Mono, monospace">${escapeHtml(text)}</text>`;
  }
```

- [ ] **Step 3: Add core visual functions**

Add:

```js
  function learningVisualCompounding() {
    return `<svg viewBox="0 0 420 190" role="img">
      <path d="M34 150H390M34 150V24" stroke="rgba(78,221,138,.25)" stroke-width="1"/>
      <path d="M40 142 C116 136 170 118 218 91 C278 57 328 33 382 24" fill="none" stroke="var(--green)" stroke-width="3"/>
      <path d="M40 150 C116 146 170 134 218 111 C278 80 328 58 382 48 L382 150 Z" fill="rgba(78,221,138,.08)"/>
      ${svgText(44, 170, 'Today', 'var(--text-dim)', 10, 'start')}
      ${svgText(382, 170, 'Later', 'var(--text-dim)', 10, 'end')}
      ${svgText(210, 18, 'Growth earns growth when gains stay invested', 'var(--text-mid)', 11)}
    </svg>`;
  }

  function learningVisualCashFlow() {
    const bars = [
      ['Income', 72, 'var(--green)'],
      ['Needs', 48, 'var(--amber)'],
      ['Debt', 26, 'var(--red)'],
      ['Save', 34, 'var(--green-dim)'],
      ['Invest', 42, 'var(--green)']
    ];
    return `<svg viewBox="0 0 420 190" role="img">
      ${bars.map((bar, index) => {
        const x = 34 + index * 76;
        const h = bar[1];
        return `<rect x="${x}" y="${150 - h}" width="46" height="${h}" rx="4" fill="${bar[2]}" opacity=".82"/>
          ${svgText(x + 23, 170, bar[0])}`;
      }).join('')}
      ${svgText(210, 22, 'Cash flow decides what jobs your dollars can do', 'var(--text-mid)', 11)}
    </svg>`;
  }

  function learningVisualDiversification() {
    return `<svg viewBox="0 0 420 190" role="img">
      ${Array.from({ length: 24 }, (_, i) => {
        const x = 42 + (i % 8) * 40;
        const y = 48 + Math.floor(i / 8) * 34;
        const fill = i === 5 ? 'var(--red)' : 'rgba(78,221,138,.55)';
        return `<rect x="${x}" y="${y}" width="25" height="22" rx="4" fill="${fill}" opacity=".9"/>`;
      }).join('')}
      ${svgText(210, 24, 'Many holdings reduce dependence on one outcome', 'var(--text-mid)', 11)}
      ${svgText(210, 172, 'Diversification cannot remove all risk, but it can reduce single-company risk', 'var(--text-dim)', 10)}
    </svg>`;
  }

  function learningVisualRiskReward() {
    const points = [
      ['Cash', 50, 126],
      ['Bonds', 126, 112],
      ['ETFs', 204, 86],
      ['Stocks', 286, 62],
      ['Options', 360, 34]
    ];
    return `<svg viewBox="0 0 420 190" role="img">
      <path d="M38 146H382" stroke="rgba(78,221,138,.22)" stroke-width="8" stroke-linecap="round"/>
      <path d="M38 146C150 132 230 82 382 34" fill="none" stroke="var(--green)" stroke-width="2"/>
      ${points.map(([label, x, y]) => `<circle cx="${x}" cy="${y}" r="7" fill="var(--bg-card-hi)" stroke="var(--green)" stroke-width="2"/>${svgText(x, y + 24, label)}`).join('')}
      ${svgText(38, 170, 'Lower volatility', 'var(--text-dim)', 10, 'start')}
      ${svgText(382, 170, 'Higher uncertainty', 'var(--text-dim)', 10, 'end')}
    </svg>`;
  }

  function learningVisualAllocation() {
    return `<svg viewBox="0 0 420 190" role="img">
      <circle cx="145" cy="95" r="58" fill="none" stroke="rgba(78,221,138,.18)" stroke-width="30"/>
      <circle cx="145" cy="95" r="58" fill="none" stroke="var(--green)" stroke-width="30" stroke-dasharray="210 365" transform="rotate(-90 145 95)"/>
      <circle cx="145" cy="95" r="58" fill="none" stroke="var(--amber)" stroke-width="30" stroke-dasharray="86 365" stroke-dashoffset="-210" transform="rotate(-90 145 95)"/>
      <circle cx="145" cy="95" r="58" fill="none" stroke="var(--red)" stroke-width="30" stroke-dasharray="34 365" stroke-dashoffset="-296" transform="rotate(-90 145 95)"/>
      ${svgText(260, 72, 'What you own', 'var(--text-mid)', 12, 'start')}
      ${svgText(260, 94, 'How much you own', 'var(--text-mid)', 12, 'start')}
      ${svgText(260, 116, 'How long you can hold', 'var(--text-mid)', 12, 'start')}
    </svg>`;
  }

  function learningVisualOptionPayoff() {
    return `<svg viewBox="0 0 420 190" role="img">
      <path d="M40 135H385M205 26V154" stroke="rgba(78,221,138,.22)" stroke-width="1"/>
      <path d="M54 135H205L364 38" fill="none" stroke="var(--green)" stroke-width="3"/>
      <path d="M54 40L205 135H364" fill="none" stroke="var(--amber)" stroke-width="3" opacity=".85"/>
      ${svgText(205, 170, 'Strike', 'var(--text-dim)', 10)}
      ${svgText(95, 32, 'Put', 'var(--amber)', 11)}
      ${svgText(330, 32, 'Call', 'var(--green)', 11)}
      ${svgText(212, 22, 'Payoff changes with price, time, and premium', 'var(--text-mid)', 11)}
    </svg>`;
  }

  function learningVisualOwnership() {
    return `<svg viewBox="0 0 420 190" role="img">
      <rect x="74" y="48" width="210" height="96" rx="8" fill="rgba(78,221,138,.10)" stroke="rgba(78,221,138,.32)"/>
      <rect x="74" y="48" width="42" height="96" rx="8" fill="var(--green)" opacity=".72"/>
      ${svgText(179, 100, 'Business', 'var(--text)', 15)}
      ${svgText(95, 166, 'Your share', 'var(--green)', 10)}
      ${svgText(308, 86, 'A stock is a claim', 'var(--text-mid)', 11, 'start')}
      ${svgText(308, 106, 'on a real company', 'var(--text-mid)', 11, 'start')}
    </svg>`;
  }
```

- [ ] **Step 4: Verify visual functions exist**

Run:

```bash
rg -n "learningVisualCompounding|learningVisualCashFlow|learningVisualDiversification|learningVisualOptionPayoff|learningVisualOwnership" apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html
```

Expected: all visual functions appear once.

- [ ] **Step 5: Commit**

```bash
git add apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html
git commit -m "feat: add learning visual explainers"
```

## Task 6: Browser Verification And Polish

**Files:**
- Modify: `apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html` only when verification exposes a concrete layout or interaction issue.

- [ ] **Step 1: Run static syntax checks**

Run:

```bash
node --check <(perl -0777 -ne 'while (/<script>(.*?)<\\/script>/sg) { print $1, "\n" }' apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html)
```

Expected: no output and exit code 0.

- [ ] **Step 2: Run backend tests to catch accidental static-resource or API regressions**

Run:

```bash
cd apps/api/trader-assistant/trading-dashboard
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Start the app for browser testing**

Run:

```bash
cd apps/api/trader-assistant/trading-dashboard
./gradlew bootRun
```

Expected: Spring Boot starts and serves `http://localhost:8080`.

- [ ] **Step 4: Verify desktop Learning UI**

In the browser:

1. Open `http://localhost:8080`.
2. Start demo mode from the login screen.
3. Open `Learning` from the sidebar.
4. Confirm the page title says `Learning`.
5. Confirm the Start Here primer, four path cards, reference search, concept list, detail panel, and visual explainer are visible.
6. Search `option`.
7. Confirm `Call Option` and `Put Option` appear.
8. Select `Call Option`.
9. Confirm the option payoff visual appears and is nonblank.

- [ ] **Step 5: Verify mobile Learning UI**

In a mobile viewport around `390x844`:

1. Confirm the bottom tab bar includes `Learning`.
2. Tap `Learning`.
3. Confirm the primer, path cards, search, concept list, detail, and visual stack vertically.
4. Confirm no text overlaps controls.
5. Confirm no horizontal scrolling is required.

- [ ] **Step 6: Verify existing navigation still works**

In the browser, visit:

1. Dashboard.
2. Quotes.
3. Portfolio.
4. Finance.
5. Journal.
6. Import.
7. Learning.

Expected: active nav state and page title update correctly for every page.

- [ ] **Step 7: Apply small polish fixes only if verification finds concrete issues**

Allowed fixes:

```css
/* Use this if mobile bottom labels are cramped after adding Learning. */
#bottom-tab-bar button {
  min-width: 0;
}
```

```css
/* Use this if Learning SVGs visually crowd the detail card. */
.learn-visual {
  overflow: hidden;
}
```

Do not add new feature scope during polish.

- [ ] **Step 8: Commit verification fixes when Step 7 changed the file**

If files changed:

```bash
git add apps/api/trader-assistant/trading-dashboard/src/main/resources/static/index.html
git commit -m "fix: polish learning responsive layout"
```

If no files changed, do not create an empty commit.

## Final Verification

Run:

```bash
git status --short
cd apps/api/trader-assistant/trading-dashboard
./gradlew test
```

Expected:

- Only intentional untracked local artifacts remain.
- `./gradlew test` reports `BUILD SUCCESSFUL`.
- Browser verification confirms Learning works on desktop and mobile.
