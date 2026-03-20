# Yahoo Finance Migration Design Spec

**Date:** 2026-03-20
**Goal:** Replace Finnhub with Yahoo Finance as the sole market data provider. Backend only — zero frontend changes.

---

## Context

The trading dashboard currently uses Finnhub for all market data (quotes, fundamentals, daily history, news). Finnhub requires a paid API key (`MARKETDATA_API_KEY`), and its free tier is capped at 60 calls/minute. Yahoo Finance provides equivalent data without an API key, eliminating the key management burden.

A previous attempt at this migration overstepped scope and modified frontend pages. This spec is strictly backend.

**Risk acknowledgement:** Yahoo Finance is an unofficial, undocumented API. Endpoints can change without notice. The health indicator will detect breakage, but there is no guaranteed SLA. This is accepted as a trade-off for eliminating the API key requirement.

## Scope

### In scope
- Replace `RealMarketDataProvider` internals (Finnhub calls -> Yahoo Finance calls)
- New `YahooCrumbProvider` for Yahoo's cookie/crumb authentication
- Remove `apiKey` from `MarketDataProperties`, `ProdSecretsValidator`, `application.yml`, `ENV.example`
- Delete `MarketDataQuotaTracker` and remove all references from consumers
- Delete `MarketDataRateLimitException` and remove all references from consumers
- Remove `/api/marketdata/quota` endpoint (from `openAPI.yaml`, controller, and tests)
- Update `DefaultQuoteService` to remove rate-limit catch blocks
- Update `ApiExceptionHandler` to remove rate-limit exception handler
- Update `MarketDataHealthIndicator` error handling
- Add Rome RSS library dependency for Yahoo news feed parsing
- Update `openAPI.yaml` source field from "Finnhub" to "Yahoo Finance"
- Clean up stale entries in `ENV.example`
- Update all affected tests

### Explicitly out of scope
- `index.html` — no frontend changes whatsoever
- `MarketDataProvider.java` interface — unchanged
- `FakeMarketDataProvider.java` — dev provider unchanged
- Record types (`Quote`, `CompanyOverview`, `DailyBar`, `NewsArticle`) — unchanged
- Crypto support (CoinGecko, routing provider, symbol mapper) — not included
- News sentiment analysis — future feature
- News thumbnails — not available via RSS, field will be null

### Known tech debt (not addressed)
- `index.html` contains the hardcoded string "Finnhub rate limit reached" (line ~1604). This message will never surface after migration since the rate-limit exception no longer exists, but the string remains. No frontend changes are in scope.

---

## Yahoo Finance Endpoints

| Data | Endpoint | Auth |
|---|---|---|
| Quote | `https://query2.finance.yahoo.com/v10/finance/quoteSummary/{symbol}?modules=price` | Cookie + crumb |
| Overview | `https://query2.finance.yahoo.com/v10/finance/quoteSummary/{symbol}?modules=price,defaultKeyStatistics,summaryDetail,assetProfile` | Cookie + crumb |
| Daily History | `https://query2.finance.yahoo.com/v8/finance/chart/{symbol}?range=100d&interval=1d` | None |
| News | `https://feeds.finance.yahoo.com/rss/2.0/headline?s={symbol}&region=US&lang=en-US` | None |

## YahooCrumbProvider

New `@Component @Profile("!dev")` class responsible for managing the session cookie + crumb token required by Yahoo's v10 `quoteSummary` endpoint.

**Lifecycle:**
1. Fetch `https://fc.yahoo.com/` -> extract `Set-Cookie` header (session cookie)
2. Fetch `https://query2.finance.yahoo.com/v1/test/getcrumb` with that cookie -> plaintext crumb string
3. Cache cookie + crumb in memory via `volatile` fields with double-checked locking (same pattern as `MarketDataHealthIndicator`)
4. On 401/403 from any Yahoo call -> `invalidate()` -> next request re-fetches fresh credentials
5. All HTTP requests use a browser-like `User-Agent` header (e.g., `Mozilla/5.0`) to avoid Yahoo rejecting non-browser clients

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
| `date` | `chart.result[0].timestamp[i]` (epoch -> LocalDate) |
| `open` | `chart.result[0].indicators.quote[0].open[i]` |
| `high` | `chart.result[0].indicators.quote[0].high[i]` |
| `low` | `chart.result[0].indicators.quote[0].low[i]` |
| `close` | `chart.result[0].indicators.quote[0].close[i]` |
| `volume` | `chart.result[0].indicators.quote[0].volume[i]` |

### NewsArticle (from Yahoo RSS)

| Record field | RSS element |
|---|---|
| `id` | `link.hashCode()` cast to `long` (or `guid.hashCode()` if guid present) |
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
| 401/403 | Invalidate crumb, retry once with fresh crumb. If still fails -> `MarketDataClientException` |
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
| `RealMarketDataProvider.java` | Rewrite internals: Finnhub -> Yahoo Finance. Constructor changes: remove `Environment` and `MarketDataQuotaTracker` params, add `YahooCrumbProvider`. Remove all retry logic and `RetryProperties` usage. Add browser-like `User-Agent` header to all requests. |
| `MarketDataProperties.java` | Remove `apiKey` field and `@NotBlank` annotation. Remove `RetryProperties` inner class entirely. Add `@NotBlank query2BaseUrl` and `@NotBlank yahooRssBaseUrl`. |
| `MarketDataHealthIndicator.java` | Remove rate-limit catch. Add `MarketDataClientException` -> UNKNOWN. |
| `DefaultQuoteService.java` | Remove all 3 `catch (MarketDataRateLimitException)` blocks. Keep stale-cache fallback but trigger it on `MarketDataClientException` instead. |
| `ApiExceptionHandler.java` | Remove `@ExceptionHandler(MarketDataRateLimitException.class)` method. |
| `QuoteController.java` | Remove `Optional<MarketDataQuotaTracker>` injection. Remove `getMarketDataQuota()` method. Remove `/api/marketdata/quota` from the hardcoded endpoints list in `getQuotesIndex()`. |
| `ProdSecretsValidator.java` | Remove `getApiKey()` call from `run()` method. Constructor still takes `MarketDataProperties` (still has `baseUrl` and other fields to validate). |
| `application.yml` | Replace Finnhub URLs/key with Yahoo URLs. Remove `api-key` and `retry` block. |
| `ENV.example` (service-level) | Remove `MARKETDATA_API_KEY`, `FINNHUB_API_KEY`, `ALPHA_VANTAGE_API_KEY`, `YAHOO_FINANCE_API_KEY`, `TRADING_MARKETDATA_BASE_URL`, and `TRADING_MARKETDATA_RETRY_*` env vars. |
| `ENV.example` (repo root) | Remove `FINNHUB_API_KEY` and `MARKETDATA_API_KEY` entries. |
| `.envrc.example` | Remove `ALPHA_VANTAGE_API_KEY`, `YAHOO_FINANCE_API_KEY`, and `MARKETDATA_API_KEY` entries. |
| `openAPI.yaml` | Change source default from "Finnhub" to "Yahoo Finance". Remove `/api/marketdata/quota` endpoint and `MarketDataQuota` schema. |
| `build.gradle` | Add `com.rometools:rome:2.1.0`. |

### New
| File | Purpose |
|---|---|
| `YahooCrumbProvider.java` | `@Component @Profile("!dev")`. Cookie + crumb lifecycle management with double-checked locking. |
| `YahooCrumbProviderTest.java` | Tests for crumb fetch, caching, invalidation on 401. Uses MockWebServer. |

### Deleted
| File | Reason |
|---|---|
| `MarketDataQuotaTracker.java` | No Yahoo rate limit to track. |
| `MarketDataRateLimitException.java` | No quota tracking; no consumers remain after updating DefaultQuoteService, ApiExceptionHandler, and QuoteController. |

### Tests Modified
| File | Change |
|---|---|
| `RealMarketDataProviderTest.java` | Rewrite stubs: Yahoo Finance JSON responses instead of Finnhub. Remove retry tests. |
| `MarketDataHealthIndicatorTest.java` | Update for UNKNOWN on client error. Update `@DynamicPropertySource` properties: `base-url` -> `query2-base-url`, remove `api-key`. |
| `ProdSecretsValidatorTest.java` | Remove `MARKETDATA_API_KEY` assertion. Update test helper to use `query2BaseUrl` instead of `baseUrl`. |
| `DefaultQuoteServiceTest.java` | Remove rate-limit exception test cases. Update fallback tests to use `MarketDataClientException`. |
| `QuoteControllerTest.java` | Remove quota endpoint tests. Remove rate-limit references. |

### Explicitly untouched
- `index.html`
- `MarketDataProvider.java`
- `FakeMarketDataProvider.java`
- `Quote.java`, `CompanyOverview.java`, `DailyBar.java`, `NewsArticle.java`
- `MarketDataClientException.java` (kept — used by new provider)
- `QuoteNotFoundException.java` (kept — used by new provider)

## Configuration

### application.yml (new trading.marketdata block)
```yaml
trading:
  marketdata:
    query2-base-url: https://query2.finance.yahoo.com
    yahoo-rss-base-url: https://feeds.finance.yahoo.com
    health-symbol: ${MARKETDATA_HEALTH_SYMBOL:SPY}
    health-cache-ttl: ${MARKETDATA_HEALTH_CACHE_TTL:PT1M}
    connect-timeout: 5s
    read-timeout: 10s
    write-timeout: 10s
```

No `api-key`. No `retry` block. No `base-url` (replaced by `query2-base-url` as the single WebClient base URL).

## Dependencies

### Added
- `com.rometools:rome:2.1.0` — RSS feed parsing for Yahoo Finance news

### Unchanged
- `spring-boot-starter-webflux` — WebClient for Yahoo HTTP calls
- `caffeine` — application-level caching (unchanged)
- `okhttp3:mockwebserver` — test stubs (unchanged)
