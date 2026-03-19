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

Injected with `Map<String, String> cryptoSymbols` from `market-data.crypto-symbols` in `application.yml`. Provides three methods:

- `isCrypto(String symbol)` — true if symbol key exists in the map (case-insensitive)
- `toCoinGeckoId(String symbol)` — returns the CoinGecko coin ID (e.g., `"BTC"` → `"bitcoin"`)
- `toYahooRssSymbol(String symbol)` — appends `-USD` for Yahoo RSS news (e.g., `"BTC"` → `"BTC-USD"`)

Initial config:
```yaml
market-data:
  crypto-symbols:
    BTC: bitcoin
    ETH: ethereum
    SOL: solana
    XRP: ripple
```

Adding a new coin requires only a one-line `application.yml` change.

### `YahooCrumbProvider`

Yahoo Finance's `v10/quoteSummary` endpoint (used for company overviews) requires a crumb token obtained via a browser-like session handshake. `YahooCrumbProvider` handles this transparently:

1. On first call, issues a GET to `https://finance.yahoo.com/` to establish a session cookie, then fetches `https://query2.finance.yahoo.com/v1/test/getcrumb`
2. Caches the returned crumb string in memory (valid for hours to days)
3. Exposes `getCrumb()` — called by `YahooFinanceMarketDataProvider` before overview requests
4. If a quoteSummary call returns HTTP 401, `YahooCrumbProvider` invalidates the cached crumb and re-fetches before retrying

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
| `getQuote` | `query1.finance.yahoo.com/v7/finance/quote?symbols={symbol}` | `quoteResponse.result[0].regularMarketPrice`, `regularMarketChangePercent`, `regularMarketTime` |
| `getDailyHistory` | `query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=3mo&interval=1d` | `chart.result[0].timestamp[]`, `indicators.quote[0].{open,high,low,close,volume}[]` |
| `getOverview` | `query2.finance.yahoo.com/v10/finance/quoteSummary/{symbol}?crumb={crumb}&modules=price,summaryProfile,summaryDetail,defaultKeyStatistics` | `price.marketCap`, `summaryProfile.sector`, `summaryDetail.trailingPE`, `defaultKeyStatistics.beta`, `defaultKeyStatistics.trailingEps`, `defaultKeyStatistics.52WeekHigh/Low` |
| `getNews` | RSS: `finance.yahoo.com/rss/headline?s={symbol}` | `<title>`, `<link>`, `<description>`, `<pubDate>`, `<source>` — parsed via Rome library |

Error mapping:
- HTTP 404 / empty result array → `QuoteNotFoundException`
- HTTP 429 → `MarketDataRateLimitException`
- Other HTTP errors → `MarketDataClientException`
- Retry-on-error behavior (prod profile only) is retained from the existing `RetryBackoffSpec` pattern

### `CoinGeckoMarketDataProvider`

`@Component @Profile("!dev")`. No API key required on free tier. No custom headers needed.

| Method | Endpoint | Notes |
|---|---|---|
| `getQuote` | `api.coingecko.com/api/v3/simple/price?ids={id}&vs_currencies=usd&include_24hr_change=true&include_last_updated_at=true` | `{id}.usd` → price; `{id}.usd_24h_change` → changePercent; `{id}.usd_last_updated_at` → timestamp |
| `getDailyHistory` | `api.coingecko.com/api/v3/coins/{id}/ohlc?vs_currency=usd&days=90` | Returns `[[timestamp_ms, open, high, low, close]]` arrays; maps directly to `DailyBar` |
| `getOverview` | `api.coingecko.com/api/v3/coins/{id}?localization=false&tickers=false&market_data=true&community_data=false&developer_data=false` | `name` → name; `"Cryptocurrency"` → sector; `market_data.market_cap.usd` → marketCap; `market_data.ath.usd` / `market_data.atl.usd` → high52/low52; P/E and EPS left null (not applicable) |
| `getNews` | Yahoo Finance RSS `finance.yahoo.com/rss/headline?s={symbol}-USD` | Delegates to same Rome-based RSS parser as `YahooFinanceMarketDataProvider` |

Error mapping: unknown coin ID (empty response object `{}`) → `QuoteNotFoundException`; HTTP 429 → `MarketDataRateLimitException`.

### `RoutingMarketDataProvider`

`@Primary @Component @Profile("!dev")`. Thin dispatcher — no parsing logic.

```
getQuote(symbol)       → isCrypto ? coingecko.getQuote(coinId)    : yahoo.getQuote(symbol)
getOverview(symbol)    → isCrypto ? coingecko.getOverview(coinId)  : yahoo.getOverview(symbol)
getDailyHistory(symbol)→ isCrypto ? coingecko.getDailyHistory(id)  : yahoo.getDailyHistory(symbol)
getNews(symbol)        → isCrypto ? coingecko.getNews(symbol)      : yahoo.getNews(symbol)
```

---

## Configuration Changes

### `MarketDataProperties`

Remove: `apiKey` field.

Add:
```yaml
market-data:
  base-url: https://query1.finance.yahoo.com       # Yahoo v7/v8 endpoints
  query2-base-url: https://query2.finance.yahoo.com # Yahoo v10/quoteSummary + crumb
  yahoo-rss-base-url: https://finance.yahoo.com     # RSS feed
  coin-gecko-base-url: https://api.coingecko.com    # CoinGecko API
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

### `ProdSecretsValidator`

Remove `MARKETDATA_API_KEY` from the required-non-blank secrets list. The validator continues to enforce `TRADING_API_KEY`, `SPRING_DATASOURCE_PASSWORD`, and `MANAGEMENT_PASSWORD`.

### `ENV.example`

Remove `MARKETDATA_API_KEY` entry.

---

## `MarketDataQuotaTracker`

Retained as-is. Its significance shifts from "hard gate" (Finnhub's 60/min free limit) to "informational rate monitor." The health indicator behavior (`UNKNOWN` on exhaustion rather than `DOWN`) remains correct for CoinGecko's 50/min free limit. At single-user scale, the limit is practically unreachable during normal use.

---

## New Dependency

```xml
<!-- Rome: RSS/Atom feed parsing for Yahoo Finance news -->
<dependency>
  <groupId>com.rometools</groupId>
  <artifactId>rome</artifactId>
  <version>2.1.0</version>
</dependency>
```

---

## Testing Strategy

All new classes are unit-tested with **MockWebServer** (OkHttp, transitively available) serving JSON/RSS fixtures. No new integration tests are required — existing `*IT.java` tests are profile-isolated and unaffected.

| Test class | Coverage |
|---|---|
| `CryptoSymbolMapperTest` | `isCrypto` true/false for known and unknown symbols; `toCoinGeckoId` for all 4 coins; `toYahooRssSymbol` |
| `YahooCrumbProviderTest` | Initial fetch, in-memory cache hit, re-fetch on 401 |
| `YahooFinanceMarketDataProviderTest` | Quote, history, overview, news parsing from fixtures; 404 → `QuoteNotFoundException`; 429 → `MarketDataRateLimitException`; blank/null price fields |
| `CoinGeckoMarketDataProviderTest` | All 4 methods from fixtures; empty response → `QuoteNotFoundException`; 429 handling |
| `RoutingMarketDataProviderTest` | BTC/ETH/SOL/XRP route to CoinGecko mock; AAPL/SPY/^GSPC route to Yahoo mock |

Deleted: `RealMarketDataProviderTest.java`

---

## Migration Steps (Production)

1. Deploy the new JAR — startup succeeds without `MARKETDATA_API_KEY`
2. Optionally remove `MARKETDATA_API_KEY` from `/etc/systemd/system/benji.service` and run `sudo systemctl daemon-reload` (leaving it is harmless)
3. Verify `/actuator/health` returns `UP` and spot-check a quote, overview, and news call via the API

No database migrations, no Flyway changes, no frontend changes.
