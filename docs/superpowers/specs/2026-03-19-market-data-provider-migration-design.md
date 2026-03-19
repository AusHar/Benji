# Market Data Provider Migration: Finnhub → Yahoo Finance + CoinGecko

**Date:** 2026-03-19
**Status:** Approved for planning

---

## Goals

- Replace the Finnhub `RealMarketDataProvider` (60 calls/min free-tier ceiling) with two free, uncapped providers: Yahoo Finance (unofficial JSON API) for stocks, ETFs, indices, and options; CoinGecko (official free API) for crypto
- Cover all five instrument types the app tracks: US equities, ETFs, indices, crypto, options
- Zero ongoing cost; no mandatory API key for either provider
- Keep the `MarketDataProvider` interface and everything above it (services, controllers, OpenAPI spec) entirely unchanged
- Add a config-driven crypto symbol map so new coins can be added without code changes

## Non-Goals

- Real-time WebSocket streaming (current polling + Caffeine cache model is retained)
- Supporting crypto exchanges other than CoinGecko's aggregated market data
- Replacing `FakeMarketDataProvider` (dev/test profile stays as-is)
- Any changes to the OpenAPI spec, DTOs, or frontend

---

## Architecture

### Provider routing

```
DefaultQuoteService
  └─ RoutingMarketDataProvider  (@Primary, @Profile("!dev"))
        ├─ CryptoSymbolMapper.isCrypto(symbol)
        │     true  → CoinGeckoMarketDataProvider
        │     false → YahooFinanceMarketDataProvider
        └─ both implement MarketDataProvider (unchanged interface)
```

`RoutingMarketDataProvider` is the only bean `DefaultQuoteService` sees. All four interface methods (`getQuote`, `getOverview`, `getDailyHistory`, `getNews`) delegate to the appropriate provider. `FakeMarketDataProvider` (active on `dev` profile) is entirely unaffected.

---

## New Components

### `CryptoSymbolMapper`

Injected with `Map<String, String> cryptoSymbols` from `trading.marketdata.crypto-symbols` in `application.yml`. Provides three methods:

- `isCrypto(String symbol)` — true if symbol key exists in the map (case-insensitive)
- `toCoinGeckoId(String symbol)` — returns the CoinGecko coin ID (e.g., `"BTC"` → `"bitcoin"`)
- `toYahooRssSymbol(String symbol)` — appends `-USD` for Yahoo RSS news (e.g., `"BTC"` → `"BTC-USD"`)

Initial config (under the existing `trading.marketdata` prefix bound to `MarketDataProperties`):
```yaml
trading:
  marketdata:
    crypto-symbols:
      BTC: bitcoin
      ETH: ethereum
      SOL: solana
      XRP: ripple
```

Adding a new coin requires only a one-line `application.yml` change.

### `YahooCrumbProvider`

Yahoo Finance's `v10/quoteSummary` endpoint (used for company overviews) requires a crumb token obtained via a browser-like session handshake. `YahooCrumbProvider` handles this transparently:

1. On first call, issues a GET to `https://finance.yahoo.com/` with browser-like headers. Extracts the `Set-Cookie` response header value and stores it in memory.
2. Issues a second GET to `https://query2.finance.yahoo.com/v1/test/getcrumb`, attaching the stored cookie value as a `Cookie` request header. The response body is the crumb string (plain text).
3. Caches the crumb string in an `AtomicReference<String>` (valid for hours to days).
4. Exposes `getCrumb()` — called by `YahooFinanceMarketDataProvider` before overview requests.
5. If a quoteSummary call returns HTTP 401, `YahooFinanceMarketDataProvider` calls `invalidateCrumb()` on this provider, then calls `getCrumb()` again to force a fresh handshake, then retries the quoteSummary request once.

**Important:** Spring `WebClient` does not maintain a cookie jar by default. The implementation must manually extract the `Set-Cookie` header from the first response and replay it as a `Cookie` header on the second request — not rely on any automatic session state in the `WebClient` instance.

The `v7/finance/quote` (quotes) and `v8/finance/chart` (history) endpoints do not require a crumb — only browser-like `User-Agent` and `Accept-Language` headers.

### `YahooFinanceMarketDataProvider`

`@Component @Profile("!dev")`. Uses Spring `WebClient` with the following default headers on all requests:
```
User-Agent: Mozilla/5.0 (compatible; trading-dashboard/1.0)
Accept: application/json
Accept-Language: en-US,en;q=0.9
```

| Method | Endpoint | Key response fields |
|---|---|---|
| `getQuote` | `query1.finance.yahoo.com/v7/finance/quote?symbols={symbol}` | `quoteResponse.result[0].regularMarketPrice` → price; `regularMarketChangePercent` → changePercent; `regularMarketTime` → timestamp (epoch seconds) |
| `getDailyHistory` | `query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=3mo&interval=1d` | `chart.result[0].timestamp[]` (epoch seconds); `indicators.quote[0].open/high/low/close/volume[]` |
| `getOverview` | `query2.finance.yahoo.com/v10/finance/quoteSummary/{symbol}?crumb={crumb}&modules=price,summaryProfile,summaryDetail,defaultKeyStatistics` | `price.marketCap.raw` → marketCap; `summaryProfile.sector` → sector; `summaryDetail.trailingPE.raw` → pe; `summaryDetail.dividendYield.raw` → dividendYield; `defaultKeyStatistics.beta.raw` → beta; `defaultKeyStatistics.trailingEps.raw` → eps; `defaultKeyStatistics.fiftyTwoWeekHigh.raw` → high52; `defaultKeyStatistics.fiftyTwoWeekLow.raw` → low52 |
| `getNews` | RSS: `finance.yahoo.com/rss/headline?s={symbol}` | `SyndEntry.title` → headline; `SyndEntry.link` → url; `SyndEntry.description.value` → summary; `SyndEntry.publishedDate` → publishedAt; `SyndEntry.source.value` → source; image → `null` (Yahoo RSS has no per-article image element); id → `(long) Math.abs(Objects.hashCode(entry.getUri()))` |

Error mapping:
- HTTP 404 / empty `quoteResponse.result` array → `QuoteNotFoundException`
- HTTP 429 → `MarketDataRateLimitException`
- HTTP 401 on quoteSummary → invalidate crumb, re-fetch, retry once; if still 401 → `MarketDataClientException`
- Other HTTP errors → `MarketDataClientException`
- Retry-on-error behavior (prod profile only, `RetryBackoffSpec` pattern) applies to both `YahooFinanceMarketDataProvider` and `CoinGeckoMarketDataProvider`, identical to the existing pattern in `RealMarketDataProvider`

### `CoinGeckoMarketDataProvider`

`@Component @Profile("!dev")`. No API key required on free tier. No custom headers needed beyond `Accept: application/json`.

| Method | Endpoint | Key response fields |
|---|---|---|
| `getQuote` | `api.coingecko.com/api/v3/simple/price?ids={id}&vs_currencies=usd&include_24hr_change=true&include_last_updated_at=true` | `{id}.usd` → price; `{id}.usd_24h_change` → changePercent; `{id}.usd_last_updated_at` → timestamp (epoch seconds) |
| `getDailyHistory` | `api.coingecko.com/api/v3/coins/{id}/ohlc?vs_currency=usd&days=90` | Returns `[[timestamp_ms, open, high, low, close]]` arrays; timestamp divided by 1000 for epoch seconds → `LocalDate` |
| `getOverview` | `api.coingecko.com/api/v3/coins/{id}?localization=false&tickers=false&market_data=true&community_data=false&developer_data=false` | `name` → name; `"Cryptocurrency"` → sector; `market_data.market_cap.usd` → marketCap; `market_data.ath.usd` → high52; `market_data.atl.usd` → low52; pe/eps/dividendYield → `null` (not applicable for crypto) |
| `getNews` | Yahoo Finance RSS `finance.yahoo.com/rss/headline?s={symbol}-USD` | Same Rome-based RSS parser as `YahooFinanceMarketDataProvider`; `CryptoSymbolMapper.toYahooRssSymbol(symbol)` converts `"BTC"` → `"BTC-USD"` |

Error mapping: empty response object `{}` for unknown coin ID → `QuoteNotFoundException`; HTTP 429 → `MarketDataRateLimitException`; retry behavior same as Yahoo provider.

### `RoutingMarketDataProvider`

`@Primary @Component @Profile("!dev")`. Thin dispatcher — no parsing logic. Constructor injects the two concrete provider types directly (not the `MarketDataProvider` interface) to avoid a circular dependency through `@Primary`:

```java
public RoutingMarketDataProvider(
    YahooFinanceMarketDataProvider yahoo,
    CoinGeckoMarketDataProvider coinGecko,
    CryptoSymbolMapper mapper) { ... }
```

Dispatch logic (all four methods follow the same pattern):
```
getQuote(symbol):
  if mapper.isCrypto(symbol):
    coinGecko.getQuote(mapper.toCoinGeckoId(symbol))
  else:
    yahoo.getQuote(symbol)
```

`getNews` for crypto passes the original symbol (e.g., `"BTC"`) to `CoinGeckoMarketDataProvider`, which internally calls `mapper.toYahooRssSymbol("BTC")` → `"BTC-USD"` before hitting the RSS feed.

---

## Configuration Changes

### `MarketDataProperties` (Java class)

**Remove:**
- `@NotBlank private String apiKey` field
- `getApiKey()` / `setApiKey()` getter and setter

**Add** the following fields (kebab-case YAML keys map to camelCase Java fields automatically via Spring's relaxed binding):

| Java field | YAML key | Type |
|---|---|---|
| `query2BaseUrl` | `query2-base-url` | `@NotBlank String` |
| `yahooRssBaseUrl` | `yahoo-rss-base-url` | `@NotBlank String` |
| `coinGeckoBaseUrl` | `coin-gecko-base-url` | `@NotBlank String` |
| `cryptoSymbols` | `crypto-symbols` | `Map<String, String>` (defaults to empty map) |

The existing `baseUrl` field (bound to `trading.marketdata.base-url`) is repurposed as the Yahoo query1 base URL (`https://query1.finance.yahoo.com`).

Full YAML block (all keys under the existing `trading.marketdata` prefix):

```yaml
trading:
  marketdata:
    base-url: https://query1.finance.yahoo.com
    query2-base-url: https://query2.finance.yahoo.com
    yahoo-rss-base-url: https://finance.yahoo.com
    coin-gecko-base-url: https://api.coingecko.com
    crypto-symbols:
      BTC: bitcoin
      ETH: ethereum
      SOL: solana
      XRP: ripple
    connect-timeout: 5s
    read-timeout: 10s
    write-timeout: 10s
    retry:
      max-attempts: 3
      initial-backoff: 500ms
      max-backoff: 5s
```

### `ProdSecretsValidator` (Java class)

**Remove:**
- The `marketDataProperties` constructor parameter and field
- The `requireSecret("MARKETDATA_API_KEY", marketDataProperties.getApiKey(), ...)` call
- The `MarketDataProperties` import

The validator continues to enforce `TRADING_API_KEY`, `SPRING_DATASOURCE_PASSWORD`, and `MANAGEMENT_PASSWORD`.

### `MarketDataHealthIndicator`

Update the hardcoded string `"Finnhub rate limit reached"` in the health detail to `"Market data rate limit reached"`.

### `MarketDataQuotaTracker`

Update `CALLS_PER_MINUTE_LIMIT` from `60` (Finnhub limit) to `50` (CoinGecko free tier limit — the more restrictive of the two providers). Update the class Javadoc to remove Finnhub references.

### `ENV.example`

Remove `MARKETDATA_API_KEY` entry.

---

## New Dependency

```groovy
// build.gradle — Rome: RSS/Atom feed parsing for Yahoo Finance news
implementation 'com.rometools:rome:2.1.0'
```

---

## Testing Strategy

All new classes are unit-tested with **MockWebServer** (OkHttp, transitively available) serving JSON/RSS fixtures. No new integration tests are required — existing `*IT.java` tests are profile-isolated and unaffected.

| Test class | Coverage |
|---|---|
| `CryptoSymbolMapperTest` | `isCrypto` true/false for known and unknown symbols; `toCoinGeckoId` for all 4 coins; `toYahooRssSymbol`; case-insensitive lookup |
| `YahooCrumbProviderTest` | Initial fetch + cookie extraction; in-memory cache hit (no second HTTP call); `invalidateCrumb()` forces re-fetch on next `getCrumb()` call |
| `YahooFinanceMarketDataProviderTest` | Quote/history/overview/news parsing from JSON/RSS fixtures; 404 → `QuoteNotFoundException`; 429 → `MarketDataRateLimitException`; 401 on overview → invalidate + retry; `fiftyTwoWeekHigh/Low` field parsing; `dividendYield` field parsing; `NewsArticle.id` is a non-zero long derived from entry URI hash |
| `CoinGeckoMarketDataProviderTest` | All 4 methods from fixtures; empty `{}` response → `QuoteNotFoundException`; 429 handling; OHLC timestamp conversion (ms → epoch seconds) |
| `RoutingMarketDataProviderTest` | BTC/ETH/SOL/XRP route to CoinGecko mock; AAPL/SPY/`^GSPC` route to Yahoo mock; unknown symbol routes to Yahoo |

Deleted: `RealMarketDataProviderTest.java`

---

## Migration Steps (Production)

1. Deploy the new JAR — startup succeeds without `MARKETDATA_API_KEY` (no longer validated)
2. Optionally remove `MARKETDATA_API_KEY` from `/etc/systemd/system/benji.service` and run `sudo systemctl daemon-reload` (leaving it is harmless)
3. Verify `/actuator/health` returns `UP` and spot-check a quote, overview, and news call via the API

No database migrations, no Flyway changes, no frontend changes.
