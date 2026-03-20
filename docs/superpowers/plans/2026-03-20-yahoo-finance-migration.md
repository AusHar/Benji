# Yahoo Finance Migration Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Finnhub with Yahoo Finance as the sole market data provider — backend only, zero frontend changes.

**Architecture:** `RealMarketDataProvider` is rewritten in-place to call Yahoo Finance endpoints. A new `YahooCrumbProvider` manages cookie/crumb authentication. `MarketDataQuotaTracker` and `MarketDataRateLimitException` are deleted along with all references. The `/api/marketdata/quota` endpoint is removed.

**Tech Stack:** Spring Boot 3, Spring WebFlux (WebClient), Rome 2.1.0 (RSS parsing), OkHttp MockWebServer (tests)

**Spec:** `docs/superpowers/specs/2026-03-20-yahoo-finance-migration-design.md`

**All commands run from:** `apps/api/trader-assistant/trading-dashboard`

**Task ordering rationale:** Tasks are ordered to minimize intermediate compile failures. The openAPI.yaml is updated before removing controller methods (so the generated interface is consistent). File deletions and consumer updates are combined into a single atomic task. Tests are updated alongside their implementations.

---

### Task 1: Dependencies & Configuration

**Files:**
- Modify: `build.gradle:30-60`
- Modify: `src/main/resources/application.yml:8-20`
- Modify: `src/main/java/com/austinharlan/trading_dashboard/config/MarketDataProperties.java`

- [ ] **Step 1: Add Rome dependency to build.gradle**

In `build.gradle`, add after line 44 (`springdoc-openapi` line):

```groovy
        implementation 'com.rometools:rome:2.1.0'
```

- [ ] **Step 2: Replace MarketDataProperties**

Replace the entire contents of `MarketDataProperties.java` with:

```java
package com.austinharlan.trading_dashboard.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "trading.marketdata")
@Validated
public class MarketDataProperties {
  @NotBlank private String query2BaseUrl;

  @NotBlank private String yahooRssBaseUrl;

  @NotBlank private String healthSymbol = "SPY";

  @NotNull private Duration healthCacheTtl = Duration.ofMinutes(1);

  @NotNull private Duration connectTimeout = Duration.ofSeconds(5);

  @NotNull private Duration readTimeout = Duration.ofSeconds(10);

  @NotNull private Duration writeTimeout = Duration.ofSeconds(10);

  public String getQuery2BaseUrl() {
    return query2BaseUrl;
  }

  public void setQuery2BaseUrl(String query2BaseUrl) {
    this.query2BaseUrl = query2BaseUrl;
  }

  public String getYahooRssBaseUrl() {
    return yahooRssBaseUrl;
  }

  public void setYahooRssBaseUrl(String yahooRssBaseUrl) {
    this.yahooRssBaseUrl = yahooRssBaseUrl;
  }

  public String getHealthSymbol() {
    return healthSymbol;
  }

  public void setHealthSymbol(String healthSymbol) {
    this.healthSymbol =
        (healthSymbol != null && !healthSymbol.isBlank()) ? healthSymbol : this.healthSymbol;
  }

  public Duration getHealthCacheTtl() {
    return healthCacheTtl;
  }

  public void setHealthCacheTtl(Duration healthCacheTtl) {
    this.healthCacheTtl = healthCacheTtl != null ? healthCacheTtl : Duration.ofMinutes(1);
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(5);
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(10);
  }

  public Duration getWriteTimeout() {
    return writeTimeout;
  }

  public void setWriteTimeout(Duration writeTimeout) {
    this.writeTimeout = writeTimeout != null ? writeTimeout : Duration.ofSeconds(10);
  }
}
```

- [ ] **Step 3: Update application.yml marketdata block**

Replace lines 8-20 of `application.yml` (the `marketdata:` block including the comment and retry section) with:

```yaml
  marketdata:
    query2-base-url: https://query2.finance.yahoo.com
    yahoo-rss-base-url: https://feeds.finance.yahoo.com
    health-symbol: ${MARKETDATA_HEALTH_SYMBOL:SPY}
    health-cache-ttl: ${MARKETDATA_HEALTH_CACHE_TTL:PT1M}
    connect-timeout: 5s
    read-timeout: 10s
    write-timeout: 10s
```

Note: timeouts are intentionally raised from Finnhub defaults (2s/5s/5s) to 5s/10s/10s since Yahoo's unofficial API can be slower.

- [ ] **Step 4: Commit**

```bash
git add build.gradle src/main/java/com/austinharlan/trading_dashboard/config/MarketDataProperties.java src/main/resources/application.yml
git commit -m "chore: update config for Yahoo Finance — new properties, remove apiKey and retry"
```

After this commit the project will not compile — expected. Subsequent tasks fix all consumers.

---

### Task 2: Create YahooCrumbProvider

**Files:**
- Create: `src/main/java/com/austinharlan/trading_dashboard/marketdata/YahooCrumbProvider.java`
- Create: `src/test/java/com/austinharlan/trading_dashboard/marketdata/YahooCrumbProviderTest.java`

- [ ] **Step 1: Write YahooCrumbProviderTest**

```java
package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YahooCrumbProviderTest {
  private MockWebServer server;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void fetchesCookieAndCrumbOnFirstCall() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "A3=d=session123; Path=/; Domain=.yahoo.com"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("test-crumb-value"));

    YahooCrumbProvider provider = new YahooCrumbProvider(server.url("/").toString());

    assertThat(provider.getCrumb()).isEqualTo("test-crumb-value");
    assertThat(provider.getCookie()).contains("A3=d=session123");
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void cachesCrumbOnSubsequentCalls() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "A3=d=session123; Path=/; Domain=.yahoo.com"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("cached-crumb"));

    YahooCrumbProvider provider = new YahooCrumbProvider(server.url("/").toString());

    String first = provider.getCrumb();
    String second = provider.getCrumb();

    assertThat(first).isEqualTo("cached-crumb");
    assertThat(second).isEqualTo("cached-crumb");
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void invalidateForcesFreshFetch() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "A3=d=first; Path=/; Domain=.yahoo.com"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("crumb-1"));
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "A3=d=second; Path=/; Domain=.yahoo.com"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("crumb-2"));

    YahooCrumbProvider provider = new YahooCrumbProvider(server.url("/").toString());

    assertThat(provider.getCrumb()).isEqualTo("crumb-1");
    provider.invalidate();
    assertThat(provider.getCrumb()).isEqualTo("crumb-2");
    assertThat(server.getRequestCount()).isEqualTo(4);
  }

  @Test
  void throwsWhenCookieFetchFails() {
    server.enqueue(new MockResponse().setResponseCode(500));

    YahooCrumbProvider provider = new YahooCrumbProvider(server.url("/").toString());

    assertThatThrownBy(provider::getCrumb).isInstanceOf(MarketDataClientException.class);
  }

  @Test
  void throwsWhenCrumbFetchFails() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "A3=d=session; Path=/; Domain=.yahoo.com"));
    server.enqueue(new MockResponse().setResponseCode(403));

    YahooCrumbProvider provider = new YahooCrumbProvider(server.url("/").toString());

    assertThatThrownBy(provider::getCrumb).isInstanceOf(MarketDataClientException.class);
  }
}
```

- [ ] **Step 2: Write YahooCrumbProvider implementation**

```java
package com.austinharlan.trading_dashboard.marketdata;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev")
public class YahooCrumbProvider {
  private static final Logger log = LoggerFactory.getLogger(YahooCrumbProvider.class);
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

  private final String baseUrl;
  private final HttpClient httpClient;
  private volatile String cookie;
  private volatile String crumb;

  public YahooCrumbProvider(
      @Value("${trading.marketdata.query2-base-url:https://query2.finance.yahoo.com}")
          String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public String getCrumb() {
    String cached = crumb;
    if (cached != null) {
      return cached;
    }
    synchronized (this) {
      cached = crumb;
      if (cached != null) {
        return cached;
      }
      refresh();
      return crumb;
    }
  }

  public String getCookie() {
    if (cookie == null) {
      getCrumb();
    }
    return cookie;
  }

  public void invalidate() {
    synchronized (this) {
      crumb = null;
      cookie = null;
    }
  }

  private void refresh() {
    try {
      String sessionCookie = fetchCookie();
      String freshCrumb = fetchCrumb(sessionCookie);
      this.cookie = sessionCookie;
      this.crumb = freshCrumb;
      log.info("Yahoo Finance crumb refreshed successfully");
    } catch (MarketDataClientException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new MarketDataClientException("Failed to refresh Yahoo Finance crumb", ex);
    }
  }

  private String fetchCookie() {
    try {
      // In production, baseUrl is https://query2.finance.yahoo.com and we need to
      // hit https://fc.yahoo.com for the cookie. In tests, baseUrl is localhost
      // and the replace is a no-op, so cookie+crumb requests both go to MockWebServer.
      String cookieUrl = baseUrl.replace("query2.finance.yahoo.com", "fc.yahoo.com");
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(cookieUrl + "/"))
              .header("User-Agent", USER_AGENT)
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      return response.headers().allValues("set-cookie").stream()
          .filter(c -> c.startsWith("A3=") || c.startsWith("A1="))
          .findFirst()
          .map(c -> c.contains(";") ? c.substring(0, c.indexOf(';')) : c)
          .orElseThrow(
              () ->
                  new MarketDataClientException(
                      "No session cookie in Yahoo response (status "
                          + response.statusCode()
                          + ")"));
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new MarketDataClientException("Failed to fetch Yahoo session cookie", ex);
    }
  }

  private String fetchCrumb(String sessionCookie) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/v1/test/getcrumb"))
              .header("User-Agent", USER_AGENT)
              .header("Cookie", sessionCookie)
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new MarketDataClientException(
            "Yahoo crumb fetch failed with status " + response.statusCode());
      }

      String body = response.body();
      if (body == null || body.isBlank()) {
        throw new MarketDataClientException("Yahoo crumb response was empty");
      }
      return body.trim();
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new MarketDataClientException("Failed to fetch Yahoo crumb", ex);
    }
  }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "*.YahooCrumbProviderTest" 2>&1 | tail -10`

Expected: All 5 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/austinharlan/trading_dashboard/marketdata/YahooCrumbProvider.java src/test/java/com/austinharlan/trading_dashboard/marketdata/YahooCrumbProviderTest.java
git commit -m "feat: add YahooCrumbProvider for cookie/crumb authentication"
```

---

### Task 3: Update openAPI.yaml

This task runs BEFORE removing controller methods, so the generated interface is updated first.

**Files:**
- Modify: `openAPI.yaml`

- [ ] **Step 1: Change source example from Finnhub to Yahoo Finance**

At line 566, change:
```yaml
          example: Finnhub
```
to:
```yaml
          example: Yahoo Finance
```

- [ ] **Step 2: Remove /api/marketdata/quota endpoint**

Remove lines 156-168 (the entire `/api/marketdata/quota` path definition).

- [ ] **Step 3: Remove MarketDataQuota schema**

Remove lines 726-741 (the entire `MarketDataQuota` schema definition, from `MarketDataQuota:` through the `example: 60` line). Be careful not to remove the `CompanyOverviewResponse` schema that follows immediately after.

- [ ] **Step 4: Regenerate OpenAPI code**

Run: `./gradlew openApiGenerate`

Expected: BUILD SUCCESSFUL. The generated `QuotesApi` interface no longer has `getMarketDataQuota()`.

- [ ] **Step 5: Commit**

```bash
git add openAPI.yaml
git commit -m "chore: remove quota endpoint and update source to Yahoo Finance in openAPI.yaml"
```

---

### Task 4: Rewrite RealMarketDataProvider + Test

Tests are written alongside the implementation to avoid a broken-test window.

**Files:**
- Modify: `src/main/java/com/austinharlan/trading_dashboard/marketdata/RealMarketDataProvider.java`
- Modify: `src/test/java/com/austinharlan/trading_dashboard/marketdata/RealMarketDataProviderTest.java`

- [ ] **Step 1: Replace RealMarketDataProvider**

Replace the entire contents of `RealMarketDataProvider.java` with:

```java
package com.austinharlan.trading_dashboard.marketdata;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

@Component
@Profile("!dev")
public class RealMarketDataProvider implements MarketDataProvider {
  private static final Logger log = LoggerFactory.getLogger(RealMarketDataProvider.class);
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

  private final WebClient webClient;
  private final WebClient rssClient;
  private final MarketDataProperties properties;
  private final YahooCrumbProvider crumbProvider;

  public RealMarketDataProvider(
      WebClient.Builder builder,
      MarketDataProperties properties,
      YahooCrumbProvider crumbProvider) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.crumbProvider = Objects.requireNonNull(crumbProvider, "crumbProvider must not be null");

    HttpClient httpClient =
        HttpClient.create()
            .option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                (int) properties.getConnectTimeout().toMillis())
            .responseTimeout(properties.getReadTimeout())
            .doOnConnected(
                conn ->
                    conn.addHandlerLast(
                            new ReadTimeoutHandler(
                                properties.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(
                            new WriteTimeoutHandler(
                                properties.getWriteTimeout().toMillis(), TimeUnit.MILLISECONDS)));

    String baseUrl = normalizeUrl(properties.getQuery2BaseUrl());

    this.webClient =
        builder
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .build();

    this.rssClient =
        WebClient.builder()
            .baseUrl(normalizeUrl(properties.getYahooRssBaseUrl()))
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .build();
  }

  @Override
  public Quote getQuote(String symbol) {
    requireSymbol(symbol);
    JsonNode result = fetchQuoteSummary(symbol, "price");
    return toQuote(symbol, result);
  }

  @Override
  public CompanyOverview getOverview(String symbol) {
    requireSymbol(symbol);
    JsonNode result =
        fetchQuoteSummary(symbol, "price,defaultKeyStatistics,summaryDetail,assetProfile");
    return toOverview(symbol, result);
  }

  @Override
  public List<DailyBar> getDailyHistory(String symbol) {
    requireSymbol(symbol);
    JsonNode response =
        webClient
            .get()
            .uri("/v8/finance/chart/{symbol}?range=100d&interval=1d", symbol)
            .retrieve()
            .onStatus(
                status -> status.value() == 404,
                r ->
                    r.bodyToMono(String.class)
                        .map(
                            body ->
                                new QuoteNotFoundException(
                                    "History was not found for %s".formatted(symbol))))
            .onStatus(
                HttpStatusCode::isError,
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(
                            body ->
                                new MarketDataClientException(
                                    "Yahoo chart error %s: %s"
                                        .formatted(r.statusCode(), body))))
            .bodyToMono(JsonNode.class)
            .doOnError(
                ex -> log.warn("Yahoo chart request for {} failed: {}", symbol, ex.getMessage()))
            .onErrorMap(
                WebClientResponseException.class,
                ex ->
                    new MarketDataClientException(
                        "Yahoo chart call failed with status %s".formatted(ex.getStatusCode()), ex))
            .onErrorMap(
                WebClientRequestException.class,
                ex ->
                    new MarketDataClientException(
                        "Yahoo chart request failed: %s".formatted(ex.getMessage()), ex))
            .block(properties.getReadTimeout());
    return toHistory(symbol, response);
  }

  @Override
  public List<NewsArticle> getNews(String symbol) {
    requireSymbol(symbol);
    try {
      String rssXml =
          rssClient
              .get()
              .uri("/rss/2.0/headline?s={symbol}&region=US&lang=en-US", symbol)
              .accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML)
              .retrieve()
              .bodyToMono(String.class)
              .block(properties.getReadTimeout());

      if (rssXml == null || rssXml.isBlank()) {
        return List.of();
      }

      SyndFeedInput input = new SyndFeedInput();
      SyndFeed feed = input.build(new StringReader(rssXml));
      return toNews(feed.getEntries());
    } catch (Exception ex) {
      log.warn("Failed to fetch Yahoo news for {}: {}", symbol, ex.getMessage());
      return List.of();
    }
  }

  // ── quoteSummary with crumb ──────────────────────────────────────────────

  private JsonNode fetchQuoteSummary(String symbol, String modules) {
    try {
      return doFetchQuoteSummary(symbol, modules);
    } catch (MarketDataClientException ex) {
      if (ex.getMessage() != null
          && (ex.getMessage().contains("401") || ex.getMessage().contains("403"))) {
        log.info("Crumb rejected, refreshing and retrying for {}", symbol);
        crumbProvider.invalidate();
        return doFetchQuoteSummary(symbol, modules);
      }
      throw ex;
    }
  }

  private JsonNode doFetchQuoteSummary(String symbol, String modules) {
    JsonNode root =
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v10/finance/quoteSummary/{symbol}")
                        .queryParam("modules", modules)
                        .queryParam("crumb", crumbProvider.getCrumb())
                        .build(symbol))
            .header("Cookie", crumbProvider.getCookie())
            .retrieve()
            .onStatus(
                status -> status.value() == 404,
                r ->
                    r.bodyToMono(String.class)
                        .map(
                            body ->
                                new QuoteNotFoundException(
                                    "Quote was not found for %s".formatted(symbol))))
            .onStatus(
                HttpStatusCode::isError,
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(
                            body ->
                                new MarketDataClientException(
                                    "Yahoo Finance error %s: %s"
                                        .formatted(r.statusCode(), body))))
            .bodyToMono(JsonNode.class)
            .doOnSubscribe(sub -> log.debug("Requesting Yahoo quoteSummary for {}", symbol))
            .doOnError(
                ex ->
                    log.warn("Yahoo quoteSummary for {} failed: {}", symbol, ex.getMessage(), ex))
            .onErrorMap(
                WebClientResponseException.class,
                ex ->
                    new MarketDataClientException(
                        "Yahoo call failed with status %s".formatted(ex.getStatusCode()), ex))
            .onErrorMap(
                WebClientRequestException.class,
                ex ->
                    new MarketDataClientException(
                        "Yahoo request failed: %s".formatted(ex.getMessage()), ex))
            .block(properties.getReadTimeout());

    if (root == null) {
      throw new QuoteNotFoundException("Quote was not found for %s".formatted(symbol));
    }

    JsonNode result = root.path("quoteSummary").path("result");
    if (!result.isArray() || result.isEmpty()) {
      throw new QuoteNotFoundException("Quote was not found for %s".formatted(symbol));
    }
    return result.get(0);
  }

  // ── Quote parsing ────────────────────────────────────────────────────────

  private Quote toQuote(String symbol, JsonNode result) {
    JsonNode price = result.path("price");
    BigDecimal marketPrice = rawBigDecimal(price, "regularMarketPrice");
    if (marketPrice == null || marketPrice.signum() == 0) {
      throw new QuoteNotFoundException("Quote was not found for %s".formatted(symbol));
    }

    BigDecimal changePercent = rawBigDecimal(price, "regularMarketChangePercent");
    long epochSeconds = price.path("regularMarketTime").asLong(0);
    Instant timestamp = epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : Instant.now();

    return new Quote(symbol, marketPrice, changePercent, timestamp);
  }

  // ── Overview parsing ─────────────────────────────────────────────────────

  private CompanyOverview toOverview(String symbol, JsonNode result) {
    JsonNode price = result.path("price");
    JsonNode stats = result.path("defaultKeyStatistics");
    JsonNode summary = result.path("summaryDetail");
    JsonNode profile = result.path("assetProfile");

    String name = safeText(price, "shortName");
    String sector = safeText(profile, "sector");
    String industry = safeText(profile, "industry");
    BigDecimal marketCap = rawBigDecimal(price, "marketCap");
    BigDecimal pe = rawBigDecimal(summary, "trailingPE");
    BigDecimal eps = rawBigDecimal(stats, "trailingEps");
    BigDecimal dividendYield = rawBigDecimal(summary, "dividendYield");
    BigDecimal beta = rawBigDecimal(stats, "beta");
    BigDecimal high52 = rawBigDecimal(summary, "fiftyTwoWeekHigh");
    BigDecimal low52 = rawBigDecimal(summary, "fiftyTwoWeekLow");

    if (name == null && marketCap == null) {
      throw new QuoteNotFoundException("Overview was not found for %s".formatted(symbol));
    }

    return new CompanyOverview(
        symbol, name, sector, industry, marketCap, pe, eps, dividendYield, beta, high52, low52);
  }

  // ── History parsing ──────────────────────────────────────────────────────

  private List<DailyBar> toHistory(String symbol, JsonNode root) {
    if (root == null) {
      throw new QuoteNotFoundException("History was not found for %s".formatted(symbol));
    }

    JsonNode results = root.path("chart").path("result");
    if (!results.isArray() || results.isEmpty()) {
      throw new QuoteNotFoundException("History was not found for %s".formatted(symbol));
    }

    JsonNode first = results.get(0);
    JsonNode timestamps = first.path("timestamp");
    JsonNode indicators = first.path("indicators").path("quote");
    if (!timestamps.isArray() || timestamps.isEmpty() || !indicators.isArray()) {
      throw new QuoteNotFoundException("History was not found for %s".formatted(symbol));
    }

    JsonNode quote = indicators.get(0);
    JsonNode opens = quote.path("open");
    JsonNode highs = quote.path("high");
    JsonNode lows = quote.path("low");
    JsonNode closes = quote.path("close");
    JsonNode volumes = quote.path("volume");

    List<DailyBar> bars = new ArrayList<>();
    for (int i = 0; i < timestamps.size(); i++) {
      try {
        if (closes.get(i) == null || closes.get(i).isNull()) {
          continue;
        }
        LocalDate date =
            Instant.ofEpochSecond(timestamps.get(i).asLong()).atZone(ZoneOffset.UTC).toLocalDate();
        bars.add(
            new DailyBar(
                date,
                BigDecimal.valueOf(opens.get(i).asDouble()),
                BigDecimal.valueOf(highs.get(i).asDouble()),
                BigDecimal.valueOf(lows.get(i).asDouble()),
                BigDecimal.valueOf(closes.get(i).asDouble()),
                volumes.get(i).asLong(0)));
      } catch (Exception ex) {
        log.warn("Skipping malformed chart entry at index {}", i, ex);
      }
    }

    bars.sort(Comparator.comparing(DailyBar::date));
    return bars;
  }

  // ── News parsing (RSS) ───────────────────────────────────────────────────

  private List<NewsArticle> toNews(List<SyndEntry> entries) {
    if (entries == null || entries.isEmpty()) {
      return List.of();
    }
    List<NewsArticle> articles = new ArrayList<>();
    for (SyndEntry entry : entries) {
      try {
        String link = entry.getLink();
        String guid = entry.getUri();
        long id = (guid != null ? guid : link != null ? link : "").hashCode();
        String headline = entry.getTitle();
        String summary =
            entry.getDescription() != null ? entry.getDescription().getValue() : null;
        Instant publishedAt =
            entry.getPublishedDate() != null
                ? entry.getPublishedDate().toInstant()
                : Instant.now();
        if (headline != null && link != null) {
          articles.add(
              new NewsArticle(id, headline, summary, "Yahoo Finance", link, null, publishedAt));
        }
      } catch (Exception ex) {
        log.warn("Skipping malformed RSS entry", ex);
      }
    }
    return articles.stream()
        .sorted(Comparator.comparing(NewsArticle::publishedAt).reversed())
        .limit(10)
        .toList();
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private void requireSymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
  }

  private static BigDecimal rawBigDecimal(JsonNode parent, String field) {
    JsonNode node = parent.path(field);
    if (node.isMissingNode() || node.isNull()) {
      return null;
    }
    JsonNode raw = node.path("raw");
    if (!raw.isMissingNode() && !raw.isNull() && raw.isNumber()) {
      return BigDecimal.valueOf(raw.asDouble());
    }
    if (node.isNumber()) {
      return BigDecimal.valueOf(node.asDouble());
    }
    return null;
  }

  private static String safeText(JsonNode parent, String field) {
    JsonNode node = parent.path(field);
    if (node.isMissingNode() || node.isNull()) {
      return null;
    }
    String text = node.asText(null);
    return (text != null && !text.isBlank()) ? text.trim() : null;
  }

  private static String normalizeUrl(String url) {
    if (url == null) {
      throw new MarketDataClientException("URL must not be null");
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
```

- [ ] **Step 2: Replace RealMarketDataProviderTest**

Replace the entire contents of `RealMarketDataProviderTest.java` with:

```java
package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class RealMarketDataProviderTest {
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private MockWebServer server;
  private MockWebServer crumbServer;
  private MarketDataProperties properties;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    crumbServer = new MockWebServer();
    crumbServer.start();

    properties = new MarketDataProperties();
    properties.setQuery2BaseUrl(server.url("/").toString());
    properties.setYahooRssBaseUrl(server.url("/").toString());
    properties.setHealthSymbol("AAPL");
    properties.setConnectTimeout(TIMEOUT);
    properties.setReadTimeout(TIMEOUT);
    properties.setWriteTimeout(TIMEOUT);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
    crumbServer.shutdown();
  }

  @Test
  void shouldReturnQuoteFromYahooQuoteSummary() {
    server.enqueue(
        jsonResponse(
            """
            {
              "quoteSummary": {
                "result": [{
                  "price": {
                    "symbol": "AAPL",
                    "shortName": "Apple Inc.",
                    "regularMarketPrice": {"raw": 189.84},
                    "regularMarketChangePercent": {"raw": 1.25},
                    "regularMarketTime": 1700000000,
                    "marketCap": {"raw": 2950000000000}
                  }
                }]
              }
            }
            """));

    RealMarketDataProvider provider = provider();
    Quote quote = provider.getQuote("AAPL");

    assertThat(quote.symbol()).isEqualTo("AAPL");
    assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("189.84"));
    assertThat(quote.changePercent()).isEqualByComparingTo(new BigDecimal("1.25"));
    assertThat(quote.timestamp()).isEqualTo(Instant.ofEpochSecond(1700000000L));
  }

  @Test
  void shouldThrowWhenYahooReturnsServerError() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));

    RealMarketDataProvider provider = provider();

    assertThatThrownBy(() -> provider.getQuote("AAPL"))
        .isInstanceOf(MarketDataClientException.class);
  }

  @Test
  void shouldThrowQuoteNotFoundWhenNoResults() {
    server.enqueue(jsonResponse("""
        {"quoteSummary": {"result": []}}
        """));

    RealMarketDataProvider provider = provider();

    assertThatThrownBy(() -> provider.getQuote("INVALID"))
        .isInstanceOf(QuoteNotFoundException.class);
  }

  @Test
  void shouldReturnOverviewFromMultipleModules() {
    server.enqueue(
        jsonResponse(
            """
            {
              "quoteSummary": {
                "result": [{
                  "price": {
                    "shortName": "Apple Inc.",
                    "marketCap": {"raw": 2950000000000}
                  },
                  "assetProfile": {
                    "sector": "Technology",
                    "industry": "Consumer Electronics"
                  },
                  "summaryDetail": {
                    "trailingPE": {"raw": 29.48},
                    "dividendYield": {"raw": 0.0055},
                    "fiftyTwoWeekHigh": {"raw": 199.62},
                    "fiftyTwoWeekLow": {"raw": 124.17}
                  },
                  "defaultKeyStatistics": {
                    "trailingEps": {"raw": 6.26},
                    "beta": {"raw": 1.2}
                  }
                }]
              }
            }
            """));

    RealMarketDataProvider provider = provider();
    CompanyOverview overview = provider.getOverview("AAPL");

    assertThat(overview.symbol()).isEqualTo("AAPL");
    assertThat(overview.name()).isEqualTo("Apple Inc.");
    assertThat(overview.sector()).isEqualTo("Technology");
    assertThat(overview.industry()).isEqualTo("Consumer Electronics");
    assertThat(overview.marketCap()).isEqualByComparingTo(new BigDecimal("2950000000000"));
    assertThat(overview.pe()).isEqualByComparingTo(new BigDecimal("29.48"));
    assertThat(overview.eps()).isEqualByComparingTo(new BigDecimal("6.26"));
    assertThat(overview.beta()).isEqualByComparingTo(new BigDecimal("1.2"));
  }

  @Test
  void shouldReturnHistoryFromChartEndpoint() {
    server.enqueue(
        jsonResponse(
            """
            {
              "chart": {
                "result": [{
                  "timestamp": [1690848000, 1690934400],
                  "indicators": {
                    "quote": [{
                      "open": [149.50, 151.00],
                      "high": [151.00, 153.00],
                      "low": [149.00, 151.50],
                      "close": [150.10, 152.50],
                      "volume": [34000000, 28000000]
                    }]
                  }
                }]
              }
            }
            """));

    RealMarketDataProvider provider = provider();
    List<DailyBar> bars = provider.getDailyHistory("AAPL");

    assertThat(bars).hasSize(2);
    assertThat(bars.get(0).close()).isEqualByComparingTo(new BigDecimal("150.1"));
    assertThat(bars.get(1).close()).isEqualByComparingTo(new BigDecimal("152.5"));
    assertThat(bars.get(0).date()).isBefore(bars.get(1).date());
  }

  @Test
  void shouldReturnEmptyNewsOnRssError() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("error"));

    RealMarketDataProvider provider = provider();
    List<NewsArticle> news = provider.getNews("AAPL");

    assertThat(news).isEmpty();
  }

  @Test
  void shouldParseRssNewsEntries() {
    String rssXml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <item>
              <title>Apple stock rises</title>
              <link>https://finance.yahoo.com/news/apple-rises</link>
              <description>Apple Inc shares gained today.</description>
              <pubDate>Thu, 01 Aug 2024 12:00:00 GMT</pubDate>
              <guid>https://finance.yahoo.com/news/apple-rises</guid>
            </item>
          </channel>
        </rss>
        """;
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/xml")
            .setBody(rssXml));

    RealMarketDataProvider provider = provider();
    List<NewsArticle> news = provider.getNews("AAPL");

    assertThat(news).hasSize(1);
    assertThat(news.get(0).headline()).isEqualTo("Apple stock rises");
    assertThat(news.get(0).source()).isEqualTo("Yahoo Finance");
    assertThat(news.get(0).url()).isEqualTo("https://finance.yahoo.com/news/apple-rises");
  }

  private RealMarketDataProvider provider() {
    YahooCrumbProvider stubCrumb = new YahooCrumbProvider(crumbServer.url("/").toString());
    crumbServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "A3=d=test; Path=/"));
    crumbServer.enqueue(new MockResponse().setResponseCode(200).setBody("test-crumb"));
    return new RealMarketDataProvider(WebClient.builder(), properties, stubCrumb);
  }

  private static MockResponse jsonResponse(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/austinharlan/trading_dashboard/marketdata/RealMarketDataProvider.java src/test/java/com/austinharlan/trading_dashboard/marketdata/RealMarketDataProviderTest.java
git commit -m "feat: rewrite RealMarketDataProvider and tests for Yahoo Finance"
```

---

### Task 5: Delete Old Files + Update All Consumers (Atomic)

All deletions and consumer updates happen in one task to avoid intermediate compile failures.

**Files:**
- Delete: `src/main/java/com/austinharlan/trading_dashboard/marketdata/MarketDataQuotaTracker.java`
- Delete: `src/main/java/com/austinharlan/trading_dashboard/marketdata/MarketDataRateLimitException.java`
- Modify: `src/main/java/com/austinharlan/trading_dashboard/service/DefaultQuoteService.java`
- Modify: `src/main/java/com/austinharlan/trading_dashboard/controllers/ApiExceptionHandler.java`
- Modify: `src/main/java/com/austinharlan/trading_dashboard/controllers/QuoteController.java`
- Modify: `src/main/java/com/austinharlan/trading_dashboard/marketdata/MarketDataHealthIndicator.java`
- Modify: `src/main/java/com/austinharlan/trading_dashboard/config/ProdSecretsValidator.java`

- [ ] **Step 1: Delete the files**

```bash
git rm src/main/java/com/austinharlan/trading_dashboard/marketdata/MarketDataQuotaTracker.java
git rm src/main/java/com/austinharlan/trading_dashboard/marketdata/MarketDataRateLimitException.java
```

- [ ] **Step 2: Update DefaultQuoteService**

In `DefaultQuoteService.java`:
- Replace import `MarketDataRateLimitException` (line 7) with `MarketDataClientException`:
  ```java
  import com.austinharlan.trading_dashboard.marketdata.MarketDataClientException;
  ```
- Change `catch (MarketDataRateLimitException ex)` to `catch (MarketDataClientException ex)` at three locations: lines 75, 98, and 124.

- [ ] **Step 3: Update ApiExceptionHandler**

In `ApiExceptionHandler.java`:
- Remove import of `MarketDataRateLimitException` (line 4)
- Remove the entire `handleMarketDataRateLimitException` method (lines 25-29)

- [ ] **Step 4: Update QuoteController**

In `QuoteController.java`:
- Remove import of `MarketDataQuotaTracker` (line 5)
- Remove import of `MarketDataQuota` (line 11)
- Remove import of `Optional` (line 21)
- Remove the `quotaTracker` field and simplify constructor:

```java
  private final QuoteService quoteService;

  public QuoteController(QuoteService quoteService) {
    this.quoteService = quoteService;
  }
```

- Remove the entire `getMarketDataQuota()` method (lines 52-60)
- Remove `"/api/marketdata/quota"` from the endpoints list in `getQuotesIndex()` (line 48). The list becomes:

```java
                List.of(
                    "/api/quotes/{symbol}",
                    "/api/quotes/{symbol}/overview",
                    "/api/quotes/{symbol}/history",
                    "/api/quotes/{symbol}/news"));
```

- [ ] **Step 5: Update MarketDataHealthIndicator**

In `MarketDataHealthIndicator.java`:
- Add import: `import com.austinharlan.trading_dashboard.marketdata.MarketDataClientException;`
- Replace the `checkProvider()` method (lines 52-65) with:

```java
  private Health checkProvider() {
    try {
      Quote quote = provider.getQuote(properties.getHealthSymbol());
      return Health.up()
          .withDetail("symbol", quote.symbol())
          .withDetail("price", quote.price())
          .withDetail("timestamp", quote.timestamp())
          .build();
    } catch (MarketDataClientException ex) {
      return Health.unknown()
          .withDetail("reason", "Cannot reach Yahoo Finance endpoint but application is healthy")
          .build();
    } catch (Exception ex) {
      return Health.down(ex).build();
    }
  }
```

- [ ] **Step 6: Update ProdSecretsValidator**

In `ProdSecretsValidator.java`, remove the `requireSecret` call for `MARKETDATA_API_KEY` (lines 51-54):

```java
    requireSecret(
        "MARKETDATA_API_KEY",
        marketDataProperties.getApiKey(),
        "Set trading.marketdata.api-key (MARKETDATA_API_KEY) before production deployment.");
```

The constructor still takes `MarketDataProperties` since it is a Spring-managed config bean and may be used for future validation.

- [ ] **Step 7: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add -u src/main/java/
git commit -m "refactor: delete QuotaTracker/RateLimitException, update all consumers"
```

---

### Task 6: Update All Tests

**Files:**
- Modify: `src/test/java/com/austinharlan/trading_dashboard/marketdata/MarketDataHealthIndicatorTest.java`
- Modify: `src/test/java/com/austinharlan/trading_dashboard/config/ProdSecretsValidatorTest.java`
- Modify: `src/test/java/com/austinharlan/trading_dashboard/service/DefaultQuoteServiceTest.java`
- Modify: `src/test/java/com/austinharlan/trading_dashboard/controllers/QuoteControllerTest.java`

- [ ] **Step 1: Update MarketDataHealthIndicatorTest**

Replace the entire contents of `MarketDataHealthIndicatorTest.java` with:

```java
package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class MarketDataHealthIndicatorTest {
  private static MockWebServer server;

  @Autowired private HealthEndpoint healthEndpoint;

  @BeforeAll
  static void startServer() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterAll
  static void shutdownServer() throws Exception {
    server.shutdown();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("trading.marketdata.query2-base-url", () -> server.url("/").toString());
    registry.add("trading.marketdata.yahoo-rss-base-url", () -> server.url("/").toString());
    registry.add("trading.marketdata.health-symbol", () -> "AAPL");
    registry.add("trading.marketdata.connect-timeout", () -> "1s");
    registry.add("trading.marketdata.read-timeout", () -> "5s");
    registry.add("trading.marketdata.write-timeout", () -> "5s");
    registry.add("trading.marketdata.health-cache-ttl", () -> "0s");
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:h2:mem:trading-dashboard-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
    registry.add("spring.datasource.username", () -> "sa");
    registry.add("spring.datasource.password", () -> "");
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
    registry.add("spring.flyway.enabled", () -> "false");
  }

  @Test
  void shouldReportUpWhenProviderResponds() {
    // YahooCrumbProvider needs cookie + crumb first
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "A3=d=test; Path=/; Domain=.yahoo.com"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("test-crumb"));
    // Then the quoteSummary response
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "quoteSummary": {
                    "result": [{
                      "price": {
                        "symbol": "AAPL",
                        "regularMarketPrice": {"raw": 189.84},
                        "regularMarketChangePercent": {"raw": 1.25},
                        "regularMarketTime": 1700000000
                      }
                    }]
                  }
                }
                """));

    HealthComponent component = healthEndpoint.healthForPath("marketData");

    assertThat(component.getStatus()).isEqualTo(Status.UP);
    Health health = (Health) component;
    assertThat(health.getDetails()).containsEntry("symbol", "AAPL");
  }

  @Test
  void shouldReportUnknownWhenProviderFails() {
    // YahooCrumbProvider needs cookie + crumb first
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "A3=d=test; Path=/; Domain=.yahoo.com"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("test-crumb"));
    // Then a server error from Yahoo
    server.enqueue(
        new MockResponse()
            .setResponseCode(500)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"error\":\"internal\"}"));

    HealthComponent component = healthEndpoint.healthForPath("marketData");

    assertThat(component.getStatus()).isEqualTo(Status.UNKNOWN);
  }

  @Test
  void shouldReuseCachedHealthWithinConfiguredTtl() {
    MarketDataProperties props = new MarketDataProperties();
    props.setQuery2BaseUrl("http://ignored");
    props.setYahooRssBaseUrl("http://ignored");
    props.setHealthSymbol("SPY");
    props.setHealthCacheTtl(Duration.ofMinutes(5));

    AtomicInteger calls = new AtomicInteger();

    MarketDataProvider provider =
        symbol -> {
          calls.incrementAndGet();
          return new Quote(symbol, BigDecimal.ONE, null, Instant.parse("2024-10-01T00:00:00Z"));
        };

    MarketDataHealthIndicator indicator = new MarketDataHealthIndicator(props, provider);

    Health first = indicator.health();
    Health second = indicator.health();

    assertThat(first.getStatus()).isEqualTo(Status.UP);
    assertThat(second.getStatus()).isEqualTo(Status.UP);
    assertThat(calls.get()).isEqualTo(1);
  }
}
```

- [ ] **Step 2: Update ProdSecretsValidatorTest**

In `ProdSecretsValidatorTest.java`, replace the `marketDataProperties` helper (lines 66-71) with:

```java
  private static MarketDataProperties marketDataProperties() {
    MarketDataProperties properties = new MarketDataProperties();
    properties.setQuery2BaseUrl("https://query2.finance.yahoo.com");
    properties.setYahooRssBaseUrl("https://feeds.finance.yahoo.com");
    return properties;
  }
```

Then update all three test methods: replace `marketDataProperties("real-marketdata-key")`, `marketDataProperties("real-key")`, and `marketDataProperties("real-marketdata-key")` with `marketDataProperties()` (no arg).

- [ ] **Step 3: Update DefaultQuoteServiceTest**

In `DefaultQuoteServiceTest.java`:
- Replace import `MarketDataRateLimitException` (line 11) with:
  ```java
  import com.austinharlan.trading_dashboard.marketdata.MarketDataClientException;
  ```
- Rename test `returnsCachedQuoteWhenRateLimitedDuringRefresh` to `returnsCachedQuoteWhenProviderFailsDuringRefresh`
- Change `new MarketDataRateLimitException("Rate limited")` to `new MarketDataClientException("Provider error")`

- [ ] **Step 4: Update QuoteControllerTest**

In `QuoteControllerTest.java`:
- Replace import `MarketDataRateLimitException` (line 9) with:
  ```java
  import com.austinharlan.trading_dashboard.marketdata.MarketDataClientException;
  ```
- In test `getQuotePropagatesProviderFailures` (line 64):
  - Change `new MarketDataRateLimitException("AlphaVantage rate limit reached")` to `new MarketDataClientException("Yahoo Finance unavailable")`
  - Change `.andExpect(status().isTooManyRequests())` to `.andExpect(status().isBadGateway())`
  - Change `.andExpect(jsonPath("$.code").value("RATE_LIMITED"))` to `.andExpect(jsonPath("$.code").value("PROVIDER_ERROR"))`

- [ ] **Step 5: Run all tests**

Run: `./gradlew test 2>&1 | tail -20`

Expected: BUILD SUCCESSFUL (all tests pass)

- [ ] **Step 6: Commit**

```bash
git add src/test/
git commit -m "test: update all tests for Yahoo Finance migration"
```

---

### Task 7: Clean Up Environment Files

**Files:**
- Modify: `ENV.example` (repo root)
- Modify: `apps/api/trader-assistant/trading-dashboard/ENV.example` (service-level)
- Modify: `apps/api/trader-assistant/trading-dashboard/.envrc.example`

- [ ] **Step 1: Clean up root ENV.example**

In `/Users/aharlan/Documents/Code/Benji/ENV.example`, replace lines 34-41 (the market data section) with:

```
# --- Market data provider ----------------------------------------------------
# Yahoo Finance — no API key required.
MARKETDATA_HEALTH_SYMBOL=SPY
MARKETDATA_HEALTH_CACHE_TTL=PT1M
```

- [ ] **Step 2: Clean up service-level ENV.example**

In `apps/api/trader-assistant/trading-dashboard/ENV.example`, replace lines 15-29 (the API Keys section) with:

```
# API Keys
# PROD-REQUIRED: replace placeholder before going live (validator will fail in prod).
TRADING_API_KEY=replace_me
TRADING_API_KEY_HEADER=X-API-KEY

# Market data — Yahoo Finance, no API key required.
MARKETDATA_HEALTH_SYMBOL=SPY
MARKETDATA_HEALTH_CACHE_TTL=PT1M
```

- [ ] **Step 3: Clean up .envrc.example**

In `apps/api/trader-assistant/trading-dashboard/.envrc.example`, remove the stale API key lines (9-14) and replace with:

```
export MARKETDATA_HEALTH_SYMBOL="SPY"
```

- [ ] **Step 4: Commit**

```bash
git add /Users/aharlan/Documents/Code/Benji/ENV.example apps/api/trader-assistant/trading-dashboard/ENV.example apps/api/trader-assistant/trading-dashboard/.envrc.example
git commit -m "chore: clean up stale API key entries from env files"
```

---

### Task 8: Update Documentation & Final Verification

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/RUNBOOK.md`

- [ ] **Step 1: Run spotlessApply**

Run: `./gradlew spotlessApply`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run full build**

Run: `./gradlew spotlessCheck build --no-daemon 2>&1 | tail -20`

Expected: BUILD SUCCESSFUL — this is the same command CI runs.

- [ ] **Step 3: Update CLAUDE.md**

In the root `CLAUDE.md`:
- Update the `marketdata/` package description from:
  ```
  - `marketdata/` — `MarketDataProvider` interface with `RealMarketDataProvider` (Finnhub) and `FakeMarketDataProvider` (dev/test); `MarketDataQuotaTracker` tracks per-minute API usage
  ```
  to:
  ```
  - `marketdata/` — `MarketDataProvider` interface with `RealMarketDataProvider` (Yahoo Finance) and `FakeMarketDataProvider` (dev/test); `YahooCrumbProvider` manages cookie/crumb authentication
  ```
- Remove `MARKETDATA_API_KEY` from the Environment variables section and the `ProdSecretsValidator` description.

- [ ] **Step 4: Update docs/RUNBOOK.md**

Make these specific changes to `docs/RUNBOOK.md`:

1. **Section 1 (line 6):** Change `integrates with Finnhub for market data` to `integrates with Yahoo Finance for market data (no API key required)`
2. **Section 2 (line 12):** Change `real Finnhub provider` to `Yahoo Finance provider`
3. **Section 3 (lines 17-18):** Remove the entire `MARKETDATA_API_KEY` row from the table
4. **Section 4 (lines 28-30):** Replace the health check descriptions:
   - Change `Returns {"status":"UNKNOWN"} when the Finnhub per-minute quota is exhausted (this is normal, not an outage).` to `Returns {"status":"UNKNOWN"} when Yahoo Finance is temporarily unreachable (this is normal, not an outage).`
   - Change `Returns {"status":"DOWN"} only when the Finnhub API itself is unreachable.` to `Returns {"status":"DOWN"} only on unexpected application errors.`
   - Remove the quota usage line: `- **Quota usage:** GET .../api/marketdata/quota`
5. **Section 7 "Health check returns UNKNOWN" (lines 61-62):** Change `Finnhub per-minute quota was exhausted` to `Yahoo Finance was temporarily unreachable`
6. **Section 7 "Quote endpoint returning stale data" (lines 64-66):** Remove `Check Finnhub quota at /api/marketdata/quota.` — just keep the cache TTL line.
7. **Section 7 "Updating a secret" (lines 68-73):** Remove the entire "Updating a secret" subsection (it was about rotating the Finnhub API key).
8. **Section 8 (lines 76-77):** Replace `Set FINNHUB_API_KEY (or MARKETDATA_API_KEY) with a key from https://finnhub.io (free tier: 60 calls/min).` with `No market data API key required — Yahoo Finance is free and unauthenticated.`

- [ ] **Step 5: Grep sweep for leftover Finnhub references**

Run: `grep -ri "finnhub\|FINNHUB\|MARKETDATA_API_KEY\|api-key\|apiKey" src/ --include="*.java" --include="*.yml" --include="*.yaml" --include="*.properties" | grep -v "test-api-key\|X-API-KEY\|api.key\|api-key-filter" | head -20`

Expected: No matches (or only false positives from unrelated code).

- [ ] **Step 6: Final clean build**

Run: `./gradlew clean build --no-daemon 2>&1 | tail -20`

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add CLAUDE.md docs/RUNBOOK.md
git commit -m "docs: update CLAUDE.md and RUNBOOK.md for Yahoo Finance migration"
```
