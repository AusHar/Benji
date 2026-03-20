# Yahoo Finance Migration Design Spec

**Date:** 2026-03-20
**Goal:** Replace Finnhub with Yahoo Finance as the sole market data provider. Backend only — zero frontend changes.

---

## Context

The trading dashboard currently uses Finnhub for all market data (quotes, fundamentals, daily history, news). Finnhub requires a paid API key (`MARKETDATA_API_KEY`), and its free tier is capped at 60 calls/minute. Yahoo Finance provides equivalent data without an API key, eliminating the key management burden.

A previous attempt at this migration overstepped scope and modified frontend pages. This spec is strictly backend.

## Scope

### In scope
- Replace `RealMarketDataProvider` internals (Finnhub calls → Yahoo Finance calls)
- New `YahooCrumbProvider` for Yahoo's cookie/crumb authentication
- Remove `apiKey` from `MarketDataProperties`, `ProdSecretsValidator`, `application.yml`, `ENV.example`
- Delete `MarketDataQuotaTracker` (no formal Yahoo rate limit to track)
- Delete `MarketDataRateLimitException` (no quota tracking)
- Update `MarketDataHealthIndicator` error handling
- Add Rome RSS library dependency for Yahoo news feed parsing
- Update `openAPI.yaml` source field from "Finnhub" to "Yahoo Finance"
- Update all affected tests

### Explicitly out of scope
- `index.html` — no frontend changes whatsoever
- `MarketDataProvider.java` interface — unchanged
- `DefaultQuoteService.java` — caching layer unchanged
- `QuoteController.java` — controller unchanged
- `FakeMarketDataProvider.java` — dev provider unchanged
- Record types (`Quote`, `CompanyOverview`, `DailyBar`, `NewsArticle`) — unchanged
- Crypto support (CoinGecko, routing provider, symbol mapper) — not included
- News sentiment analysis — future feature
- News thumbnails — not available via RSS, field will be null

---

## Yahoo Finance Endpoints

| Data | Endpoint | Auth |
|---|---|---|
| Quote | `https://query2.finance.yahoo.com/v10/finance/quoteSummary/{symbol}?modules=price` | Cookie + crumb |
| Overview | `https://query2.finance.yahoo.com/v10/finance/quoteSummary/{symbol}?modules=price,defaultKeyStatistics,summaryDetail,assetProfile` | Cookie + crumb |
| Daily History | `https://query2.finance.yahoo.com/v8/finance/chart/{symbol}?range=100d&interval=1d` | None |
| News | `https://feeds.finance.yahoo.com/rss/2.0/headline?s={symbol}&region=US&lang=en-US` | None |

## YahooCrumbProvider

New class responsible for managing the session cookie + crumb token required by Yahoo's v10 `quoteSummary` endpoint.

**Lifecycle:**
1. Fetch `https://fc.yahoo.com/` → extract `Set-Cookie` header (session cookie)
2. Fetch `https://query2.finance.yahoo.com/v1/test/getcrumb` with that cookie → plaintext crumb string
3. Cache cookie + crumb in memory
4. On 401/403 from any Yahoo call → `invalidate()` → next request re-fetches fresh credentials
5. Thread-safe via synchronization

**Interface:**
```java
public class YahooCrumbProvider {
    String getCrumb();       // returns cached or fetches fresh
    String getCookie();      // returns the session cookie
    void invalidate();       // forces re-fetch on next call
}
```

## Field Mapping

### Quote (from `quoteSummary?modules=price`)

| Record field | Yahoo JSON path |
|---|---|
| `symbol` | `price.symbol` |
| `price` | `price.regularMarketPrice.raw` |
| `changePercent` | `price.regularMarketChangePercent.raw` |
| `timestamp` | `price.regularMarketTime` (epoch seconds) |

### CompanyOverview (from `quoteSummary?modules=price,defaultKeyStatistics,summaryDetail,assetProfile`)

| Record field | Yahoo JSON path |
|---|---|
| `name` | `price.shortName` |
| `sector` | `assetProfile.sector` |
| `industry` | `assetProfile.industry` |
| `marketCap` | `price.marketCap.raw` |
| `pe` | `summaryDetail.trailingPE.raw` |
| `eps` | `defaultKeyStatistics.trailingEps.raw` |
| `dividendYield` | `summaryDetail.dividendYield.raw` |
| `beta` | `defaultKeyStatistics.beta.raw` |
| `fiftyTwoWeekHigh` | `summaryDetail.fiftyTwoWeekHigh.raw` |
| `fiftyTwoWeekLow` | `summaryDetail.fiftyTwoWeekLow.raw` |

### DailyBar (from `/v8/finance/chart/{symbol}`)

| Record field | Yahoo JSON path |
|---|---|
| `date` | `chart.result[0].timestamp[i]` (epoch → LocalDate) |
| `open` | `chart.result[0].indicators.quote[0].open[i]` |
| `high` | `chart.result[0].indicators.quote[0].high[i]` |
| `low` | `chart.result[0].indicators.quote[0].low[i]` |
| `close` | `chart.result[0].indicators.quote[0].close[i]` |
| `volume` | `chart.result[0].indicators.quote[0].volume[i]` |

### NewsArticle (from Yahoo RSS)

| Record field | RSS element |
|---|---|
| `id` | Hash of `<guid>` or `<link>` |
| `headline` | `<title>` |
| `summary` | `<description>` |
| `source` | `"Yahoo Finance"` (static) |
| `url` | `<link>` |
| `image` | `null` (not available in RSS) |
| `publishedAt` | `<pubDate>` |

## Error Handling

| Scenario | Behavior |
|---|---|
| Valid response | Parse and return |
| 401/403 | Invalidate crumb, retry once with fresh crumb. If still fails → `MarketDataClientException` |
| 404 (bad symbol) | `QuoteNotFoundException` |
| 5xx | `MarketDataClientException` |
| Network timeout / unreachable | `MarketDataClientException` |
| RSS empty or unparseable | Return empty news list |

## Health Indicator

| Exception type | Health status |
|---|---|
| None (success) | UP — includes symbol, price, timestamp details |
| `MarketDataClientException` | UNKNOWN — "Cannot reach Yahoo Finance endpoint but application is healthy" |
| Any other `Exception` | DOWN |

`MarketDataRateLimitException` catch is removed (exception class deleted).

## Files Changed

### Modified
| File | Change |
|---|---|
| `RealMarketDataProvider.java` | Rewrite internals: Finnhub → Yahoo Finance. Same class, same annotations, same interface. |
| `MarketDataProperties.java` | Remove `apiKey`. Add `query2BaseUrl`, `yahooRssBaseUrl`. Remove retry config. |
| `MarketDataHealthIndicator.java` | Remove rate-limit catch. Add `MarketDataClientException` → UNKNOWN. |
| `ProdSecretsValidator.java` | Remove `MARKETDATA_API_KEY` from required secrets. |
| `application.yml` | Replace Finnhub URLs/key with Yahoo URLs. Remove retry config. |
| `ENV.example` | Remove `MARKETDATA_API_KEY` / `FINNHUB_API_KEY`. |
| `openAPI.yaml` | Change source default from "Finnhub" to "Yahoo Finance". |
| `build.gradle` | Add `com.rometools:rome:2.1.0`. |

### New
| File | Purpose |
|---|---|
| `YahooCrumbProvider.java` | Cookie + crumb lifecycle management. |
| `YahooCrumbProviderTest.java` | Tests for crumb fetch, caching, invalidation on 401. |

### Deleted
| File | Reason |
|---|---|
| `MarketDataQuotaTracker.java` | No Yahoo rate limit to track. |
| `MarketDataRateLimitException.java` | No quota tracking. |

### Tests Modified
| File | Change |
|---|---|
| `RealMarketDataProviderTest.java` | Rewrite stubs: Yahoo Finance JSON responses instead of Finnhub. |
| `MarketDataHealthIndicatorTest.java` | Update for UNKNOWN on client error. |
| `ProdSecretsValidatorTest.java` | Remove `MARKETDATA_API_KEY` assertion. |

### Explicitly untouched
- `index.html`
- `MarketDataProvider.java`
- `DefaultQuoteService.java`
- `QuoteController.java`
- `FakeMarketDataProvider.java`
- `Quote.java`, `CompanyOverview.java`, `DailyBar.java`, `NewsArticle.java`
- `MarketDataClientException.java` (kept — used by new provider)
- `QuoteNotFoundException.java` (kept — used by new provider)

## Configuration

### application.yml (new trading.marketdata block)
```yaml
trading:
  marketdata:
    base-url: https://query2.finance.yahoo.com/v10/finance/quoteSummary
    query2-base-url: https://query2.finance.yahoo.com
    yahoo-rss-base-url: https://feeds.finance.yahoo.com
    health-symbol: ${MARKETDATA_HEALTH_SYMBOL:SPY}
    health-cache-ttl: ${MARKETDATA_HEALTH_CACHE_TTL:PT1M}
    connect-timeout: 5s
    read-timeout: 10s
    write-timeout: 10s
```

No `api-key`. No `retry` block.

## Dependencies

### Added
- `com.rometools:rome:2.1.0` — RSS feed parsing for Yahoo Finance news

### Unchanged
- `spring-boot-starter-webflux` — WebClient for Yahoo HTTP calls
- `caffeine` — application-level caching (unchanged)
- `okhttp3:mockwebserver` — test stubs (unchanged)
