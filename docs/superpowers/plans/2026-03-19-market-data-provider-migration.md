# Market Data Provider Migration Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `RealMarketDataProvider` (Finnhub) with `YahooFinanceMarketDataProvider` + `CoinGeckoMarketDataProvider` routed by `RoutingMarketDataProvider`, eliminating the API key requirement.

**Architecture:** `RoutingMarketDataProvider` (@Primary, !dev) inspects each symbol via `CryptoSymbolMapper` and delegates to either `YahooFinanceMarketDataProvider` (stocks/ETFs/indices/options) or `CoinGeckoMarketDataProvider` (crypto). Yahoo Finance RSS powers news for both. `YahooCrumbProvider` handles the session-cookie/crumb handshake required by Yahoo's `quoteSummary` endpoint.

**Tech Stack:** Spring Boot 3, Spring WebFlux (`WebClient`), Rome 2.1.0 (RSS parsing), OkHttp MockWebServer (tests), Mockito (provider unit tests)

---

### Task 1: Dependencies & Config Housekeeping

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/java/com/austinharlan/trading_dashboard/config/MarketDataProperties.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/austinharlan/trading_dashboard/config/ProdSecretsValidator.java`
- Modify: `src/test/java/com/austinharlan/trading_dashboard/config/ProdSecretsValidatorTest.java`
- Modify: `src/main/java/com/austinharlan/trading_dashboard/marketdata/MarketDataQuotaTracker.java`
- Modify: `src/main/java/com/austinharlan/trading_dashboard/marketdata/MarketDataHealthIndicator.java`
- Modify: `ENV.example`

- [ ] **Step 1: Add Rome dependency to build.gradle**

In `build.gradle`, add after the last `implementation` line in the `dependencies` block:

```groovy
implementation 'com.rometools:rome:2.1.0'
```

- [ ] **Step 2: Update MarketDataProperties — remove apiKey, add new URL fields**

Replace the contents of `MarketDataProperties.java` with:

```java
package com.austinharlan.trading_dashboard.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "trading.marketdata")
@Validated
public class MarketDataProperties {
  @NotBlank private String baseUrl;

  @NotBlank private String query2BaseUrl;

  @NotBlank private String yahooRssBaseUrl;

  @NotBlank private String coinGeckoBaseUrl;

  private Map<String, String> cryptoSymbols = new HashMap<>();

  @NotBlank private String healthSymbol = "SPY";

  @NotNull private Duration healthCacheTtl = Duration.ofMinutes(1);

  @NotNull private Duration connectTimeout = Duration.ofSeconds(5);

  @NotNull private Duration readTimeout = Duration.ofSeconds(10);

  @NotNull private Duration writeTimeout = Duration.ofSeconds(10);

  private RetryProperties retry = new RetryProperties();

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

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

  public String getCoinGeckoBaseUrl() {
    return coinGeckoBaseUrl;
  }

  public void setCoinGeckoBaseUrl(String coinGeckoBaseUrl) {
    this.coinGeckoBaseUrl = coinGeckoBaseUrl;
  }

  public Map<String, String> getCryptoSymbols() {
    return cryptoSymbols;
  }

  public void setCryptoSymbols(Map<String, String> cryptoSymbols) {
    this.cryptoSymbols = cryptoSymbols != null ? cryptoSymbols : new HashMap<>();
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

  public RetryProperties getRetry() {
    return retry;
  }

  public void setRetry(RetryProperties retry) {
    this.retry = retry != null ? retry : new RetryProperties();
  }

  public static class RetryProperties {
    @Positive private int maxAttempts = 3;

    @NotNull private Duration initialBackoff = Duration.ofMillis(500);

    @NotNull private Duration maxBackoff = Duration.ofSeconds(5);

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts > 0 ? maxAttempts : 3;
    }

    public Duration getInitialBackoff() {
      return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {
      this.initialBackoff = initialBackoff != null ? initialBackoff : Duration.ofMillis(500);
    }

    public Duration getMaxBackoff() {
      return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
      this.maxBackoff = maxBackoff != null ? maxBackoff : Duration.ofSeconds(5);
    }
  }
}
```

- [ ] **Step 3: Update application.yml — replace Finnhub URLs and remove api-key**

Replace the `trading.marketdata` block (lines 8-20) in `application.yml` with:

```yaml
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
    health-symbol: ${MARKETDATA_HEALTH_SYMBOL:SPY}
    health-cache-ttl: ${MARKETDATA_HEALTH_CACHE_TTL:PT1M}
    connect-timeout: 5s
    read-timeout: 10s
    write-timeout: 10s
    retry:
      max-attempts: ${TRADING_MARKETDATA_RETRY_MAX_ATTEMPTS:3}
      initial-backoff: ${TRADING_MARKETDATA_RETRY_INITIAL_BACKOFF:PT0.5S}
      max-backoff: ${TRADING_MARKETDATA_RETRY_MAX_BACKOFF:PT5S}
```

- [ ] **Step 4: Update ProdSecretsValidator — remove MarketDataProperties dependency**

Replace the contents of `ProdSecretsValidator.java` with:

```java
package com.austinharlan.trading_dashboard.config;

import java.util.Locale;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
class ProdSecretsValidator implements ApplicationRunner {
  private static final Set<String> PLACEHOLDER_VALUES =
      Set.of(
          "changeme",
          "change_me",
          "change-me",
          "replace_me",
          "replace-me",
          "demo",
          "your_real_key",
          "your-api-key",
          "your_api_key",
          "your-strong-db-password",
          "your_strong_db_password",
          "your-strong-actuator-password",
          "your_strong_actuator_password",
          "local-dev-key");

  private final Environment environment;
  private final ApiSecurityProperties apiSecurityProperties;

  ProdSecretsValidator(Environment environment, ApiSecurityProperties apiSecurityProperties) {
    this.environment = environment;
    this.apiSecurityProperties = apiSecurityProperties;
  }

  @Override
  public void run(ApplicationArguments args) {
    requireSecret(
        "TRADING_API_KEY",
        apiSecurityProperties.getKey(),
        "Set trading.api.key (TRADING_API_KEY) before production deployment.");
    requireSecret(
        "SPRING_DATASOURCE_PASSWORD",
        environment.getProperty("spring.datasource.password"),
        "Set SPRING_DATASOURCE_PASSWORD before production deployment.");
    requireSecret(
        "MANAGEMENT_PASSWORD",
        environment.getProperty("spring.security.user.password"),
        "Set MANAGEMENT_PASSWORD before production deployment.");
  }

  private void requireSecret(String name, String value, String guidance) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException("PROD-REQUIRED: " + name + " is not set. " + guidance);
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (PLACEHOLDER_VALUES.contains(normalized)) {
      throw new IllegalStateException(
          "PROD-REQUIRED: "
              + name
              + " is using a placeholder value. "
              + guidance
              + " Replace it before going live.");
    }
  }
}
```

- [ ] **Step 5: Update ProdSecretsValidatorTest — remove marketDataProperties from all constructor calls**

Replace the contents of `ProdSecretsValidatorTest.java` with:

```java
package com.austinharlan.trading_dashboard.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class ProdSecretsValidatorTest {

  @Test
  void runRejectsPlaceholderTradingApiKey() {
    ProdSecretsValidator validator =
        new ProdSecretsValidator(baseEnvironment(), apiSecurityProperties("replace_me"));

    assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PROD-REQUIRED: TRADING_API_KEY")
        .hasMessageContaining("placeholder value");
  }

  @Test
  void runRejectsMissingDatasourcePassword() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("spring.security.user.password", "strong-management-password");

    ProdSecretsValidator validator =
        new ProdSecretsValidator(environment, apiSecurityProperties("real-api-key"));

    assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PROD-REQUIRED: SPRING_DATASOURCE_PASSWORD")
        .hasMessageContaining("not set");
  }

  @Test
  void runAllowsRealSecrets() {
    ProdSecretsValidator validator =
        new ProdSecretsValidator(baseEnvironment(), apiSecurityProperties("real-api-key"));

    assertThatCode(() -> validator.run(new DefaultApplicationArguments(new String[0])))
        .doesNotThrowAnyException();
  }

  private static MockEnvironment baseEnvironment() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("spring.datasource.password", "strong-db-password");
    environment.setProperty("spring.security.user.password", "strong-management-password");
    return environment;
  }

  private static ApiSecurityProperties apiSecurityProperties(String key) {
    ApiSecurityProperties properties = new ApiSecurityProperties();
    properties.setKey(key);
    return properties;
  }
}
```

- [ ] **Step 6: Update MarketDataQuotaTracker — limit 60 → 50, update Javadoc**

Edit `MarketDataQuotaTracker.java`: change the Javadoc and constant:

```java
/**
 * Tracks the number of market data API calls made in the current one-minute window. Thread-safe,
 * resets whenever a full minute has elapsed since the window started.
 */
@Component
@Profile("!dev")
public class MarketDataQuotaTracker {

  public static final int CALLS_PER_MINUTE_LIMIT = 50;
```

- [ ] **Step 7: Update MarketDataHealthIndicator — update rate limit string**

In `MarketDataHealthIndicator.java` line 61, change:
```java
      return Health.unknown().withDetail("reason", "Finnhub rate limit reached").build();
```
to:
```java
      return Health.unknown().withDetail("reason", "Market data rate limit reached").build();
```

- [ ] **Step 8: Clean up ENV.example**

Remove these lines from `ENV.example`:
```
TRADING_MARKETDATA_BASE_URL=https://www.alphavantage.co
MARKETDATA_API_KEY=replace_me
ALPHA_VANTAGE_API_KEY=replace_me
YAHOO_FINANCE_API_KEY=replace_me
```

- [ ] **Step 9: Run spotlessApply and verify build compiles**

```bash
cd apps/api/trader-assistant/trading-dashboard
./gradlew spotlessApply
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL (no Finnhub references left; `ProdSecretsValidatorTest` compiles with 2-arg constructor)

- [ ] **Step 10: Commit**

```bash
git add build.gradle ENV.example \
  src/main/java/com/austinharlan/trading_dashboard/config/MarketDataProperties.java \
  src/main/java/com/austinharlan/trading_dashboard/config/ProdSecretsValidator.java \
  src/main/java/com/austinharlan/trading_dashboard/marketdata/MarketDataQuotaTracker.java \
  src/main/java/com/austinharlan/trading_dashboard/marketdata/MarketDataHealthIndicator.java \
  src/main/resources/application.yml \
  src/test/java/com/austinharlan/trading_dashboard/config/ProdSecretsValidatorTest.java
git commit -m "chore: remove Finnhub dependency; add Yahoo/CoinGecko config skeleton"
```

---

### Task 2: CryptoSymbolMapper

**Files:**
- Create: `src/main/java/com/austinharlan/trading_dashboard/marketdata/CryptoSymbolMapper.java`
- Create: `src/test/java/com/austinharlan/trading_dashboard/marketdata/CryptoSymbolMapperTest.java`

- [ ] **Step 1: Write the failing test**

Create `CryptoSymbolMapperTest.java`:

```java
package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CryptoSymbolMapperTest {

  private CryptoSymbolMapper mapper;

  @BeforeEach
  void setUp() {
    MarketDataProperties properties = new MarketDataProperties();
    properties.setBaseUrl("https://query1.finance.yahoo.com");
    properties.setQuery2BaseUrl("https://query2.finance.yahoo.com");
    properties.setYahooRssBaseUrl("https://finance.yahoo.com");
    properties.setCoinGeckoBaseUrl("https://api.coingecko.com");
    properties.setCryptoSymbols(
        Map.of("BTC", "bitcoin", "ETH", "ethereum", "SOL", "solana", "XRP", "ripple"));
    mapper = new CryptoSymbolMapper(properties);
  }

  @Test
  void isCryptoReturnsTrueForKnownSymbols() {
    assertThat(mapper.isCrypto("BTC")).isTrue();
    assertThat(mapper.isCrypto("ETH")).isTrue();
    assertThat(mapper.isCrypto("SOL")).isTrue();
    assertThat(mapper.isCrypto("XRP")).isTrue();
  }

  @Test
  void isCryptoReturnsFalseForStocksAndNull() {
    assertThat(mapper.isCrypto("AAPL")).isFalse();
    assertThat(mapper.isCrypto("SPY")).isFalse();
    assertThat(mapper.isCrypto("^GSPC")).isFalse();
    assertThat(mapper.isCrypto(null)).isFalse();
  }

  @Test
  void isCryptoIsCaseInsensitive() {
    assertThat(mapper.isCrypto("btc")).isTrue();
    assertThat(mapper.isCrypto("Eth")).isTrue();
  }

  @Test
  void toCoinGeckoIdMapsAllFourCoins() {
    assertThat(mapper.toCoinGeckoId("BTC")).isEqualTo("bitcoin");
    assertThat(mapper.toCoinGeckoId("ETH")).isEqualTo("ethereum");
    assertThat(mapper.toCoinGeckoId("SOL")).isEqualTo("solana");
    assertThat(mapper.toCoinGeckoId("XRP")).isEqualTo("ripple");
  }

  @Test
  void toYahooRssSymbolAppendsDashUsd() {
    assertThat(mapper.toYahooRssSymbol("BTC")).isEqualTo("BTC-USD");
    assertThat(mapper.toYahooRssSymbol("eth")).isEqualTo("ETH-USD");
  }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew test --tests "*.CryptoSymbolMapperTest"
```
Expected: FAIL — `CryptoSymbolMapper` does not exist

- [ ] **Step 3: Implement CryptoSymbolMapper**

Create `CryptoSymbolMapper.java`:

```java
package com.austinharlan.trading_dashboard.marketdata;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CryptoSymbolMapper {

  private final Map<String, String> cryptoSymbols;

  public CryptoSymbolMapper(MarketDataProperties properties) {
    Map<String, String> symbols = properties.getCryptoSymbols();
    this.cryptoSymbols = symbols != null ? symbols : Map.of();
  }

  public boolean isCrypto(String symbol) {
    return symbol != null && cryptoSymbols.containsKey(symbol.toUpperCase(Locale.ROOT));
  }

  public String toCoinGeckoId(String symbol) {
    if (symbol == null) return null;
    return cryptoSymbols.get(symbol.toUpperCase(Locale.ROOT));
  }

  public String toYahooRssSymbol(String symbol) {
    if (symbol == null) return null;
    return symbol.toUpperCase(Locale.ROOT) + "-USD";
  }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew test --tests "*.CryptoSymbolMapperTest"
```
Expected: 5 tests, all PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/austinharlan/trading_dashboard/marketdata/CryptoSymbolMapper.java \
  src/test/java/com/austinharlan/trading_dashboard/marketdata/CryptoSymbolMapperTest.java
git commit -m "feat: add CryptoSymbolMapper for BTC/ETH/SOL/XRP routing"
```

---

### Task 3: YahooCrumbProvider

**Files:**
- Create: `src/main/java/com/austinharlan/trading_dashboard/marketdata/YahooCrumbProvider.java`
- Create: `src/test/java/com/austinharlan/trading_dashboard/marketdata/YahooCrumbProviderTest.java`

- [ ] **Step 1: Write the failing test**

Create `YahooCrumbProviderTest.java`:

```java
package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.io.IOException;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class YahooCrumbProviderTest {

  private MockWebServer server;
  private YahooCrumbProvider provider;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    String url = server.url("/").toString();
    MarketDataProperties properties = new MarketDataProperties();
    // Point all URL fields to the same MockWebServer for simplicity
    properties.setBaseUrl(url);
    properties.setQuery2BaseUrl(url);
    properties.setYahooRssBaseUrl(url);
    properties.setCoinGeckoBaseUrl(url);
    properties.setConnectTimeout(Duration.ofSeconds(2));
    properties.setReadTimeout(Duration.ofSeconds(2));
    properties.setWriteTimeout(Duration.ofSeconds(2));

    provider = new YahooCrumbProvider(WebClient.builder(), properties);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void getCrumbFetchesCookieThenCrumb() throws InterruptedException {
    server.enqueue(
        new MockResponse().setResponseCode(200).addHeader("Set-Cookie", "A=1; Path=/; Secure"));
    server.enqueue(
        new MockResponse().setResponseCode(200).setBody("test-crumb-abc123"));

    String crumb = provider.getCrumb();

    assertThat(crumb).isEqualTo("test-crumb-abc123");
    assertThat(server.getRequestCount()).isEqualTo(2);

    server.takeRequest(); // home page request
    RecordedRequest crumbRequest = server.takeRequest();
    assertThat(crumbRequest.getHeader("Cookie")).contains("A=1");
  }

  @Test
  void getCrumbReturnsCachedValueWithoutHittingServer() {
    server.enqueue(
        new MockResponse().setResponseCode(200).addHeader("Set-Cookie", "A=1; Path=/"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("cached-crumb"));

    String first = provider.getCrumb();
    String second = provider.getCrumb();

    assertThat(first).isEqualTo("cached-crumb");
    assertThat(second).isEqualTo("cached-crumb");
    // Only 2 HTTP requests total, not 4
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void invalidateCrumbForcesFreshFetchOnNextCall() {
    // First fetch
    server.enqueue(
        new MockResponse().setResponseCode(200).addHeader("Set-Cookie", "A=1; Path=/"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("crumb-v1"));
    provider.getCrumb();

    provider.invalidateCrumb();

    // Second fetch after invalidation
    server.enqueue(
        new MockResponse().setResponseCode(200).addHeader("Set-Cookie", "A=2; Path=/"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("crumb-v2"));
    String refreshed = provider.getCrumb();

    assertThat(refreshed).isEqualTo("crumb-v2");
    assertThat(server.getRequestCount()).isEqualTo(4);
  }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew test --tests "*.YahooCrumbProviderTest"
```
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement YahooCrumbProvider**

Create `YahooCrumbProvider.java`:

```java
package com.austinharlan.trading_dashboard.marketdata;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
@Profile("!dev")
public class YahooCrumbProvider {

  private static final Logger log = LoggerFactory.getLogger(YahooCrumbProvider.class);
  private static final String USER_AGENT = "Mozilla/5.0 (compatible; trading-dashboard/1.0)";

  private final WebClient homeClient;
  private final WebClient query2Client;
  private final Duration readTimeout;
  private final AtomicReference<String> crumbCache = new AtomicReference<>();

  public YahooCrumbProvider(WebClient.Builder builder, MarketDataProperties properties) {
    this.readTimeout = properties.getReadTimeout();

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

    ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

    this.homeClient =
        builder
            .baseUrl(normalizeUrl(properties.getYahooRssBaseUrl()))
            .clientConnector(connector)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
            .build();

    this.query2Client =
        WebClient.builder()
            .baseUrl(normalizeUrl(properties.getQuery2BaseUrl()))
            .clientConnector(connector)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .build();
  }

  public String getCrumb() {
    String cached = crumbCache.get();
    if (cached != null) return cached;
    return fetchCrumb();
  }

  public void invalidateCrumb() {
    crumbCache.set(null);
  }

  private synchronized String fetchCrumb() {
    String cached = crumbCache.get();
    if (cached != null) return cached;

    String cookie =
        homeClient
            .get()
            .uri("/")
            .exchangeToMono(
                response -> {
                  String setCookie =
                      response.headers().asHttpHeaders().getFirst(HttpHeaders.SET_COOKIE);
                  return Mono.justOrEmpty(setCookie).defaultIfEmpty("");
                })
            .doOnError(ex -> log.warn("Failed to fetch Yahoo session cookie: {}", ex.getMessage()))
            .block(readTimeout);

    String crumb =
        query2Client
            .get()
            .uri("/v1/test/getcrumb")
            .header(HttpHeaders.COOKIE, cookie != null ? cookie : "")
            .retrieve()
            .bodyToMono(String.class)
            .doOnError(ex -> log.warn("Failed to fetch Yahoo crumb: {}", ex.getMessage()))
            .block(readTimeout);

    log.debug("Fetched fresh Yahoo crumb");
    crumbCache.set(crumb);
    return crumb;
  }

  private static String normalizeUrl(String url) {
    if (url == null) throw new MarketDataClientException("URL must not be null");
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew test --tests "*.YahooCrumbProviderTest"
```
Expected: 3 tests, all PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/austinharlan/trading_dashboard/marketdata/YahooCrumbProvider.java \
  src/test/java/com/austinharlan/trading_dashboard/marketdata/YahooCrumbProviderTest.java
git commit -m "feat: add YahooCrumbProvider for quoteSummary session handshake"
```

---

### Task 4: YahooFinanceMarketDataProvider

**Files:**
- Create: `src/main/java/com/austinharlan/trading_dashboard/marketdata/YahooFinanceMarketDataProvider.java`
- Create: `src/test/java/com/austinharlan/trading_dashboard/marketdata/YahooFinanceMarketDataProviderTest.java`

- [ ] **Step 1: Write the failing tests**

Create `YahooFinanceMarketDataProviderTest.java`:

```java
package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

class YahooFinanceMarketDataProviderTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private MockWebServer server;
  private MarketDataProperties properties;
  private YahooCrumbProvider mockCrumb;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    String url = server.url("/").toString();
    properties = new MarketDataProperties();
    properties.setBaseUrl(url);
    properties.setQuery2BaseUrl(url);
    properties.setYahooRssBaseUrl(url);
    properties.setCoinGeckoBaseUrl(url);
    properties.setConnectTimeout(TIMEOUT);
    properties.setReadTimeout(TIMEOUT);
    properties.setWriteTimeout(TIMEOUT);
    properties.getRetry().setInitialBackoff(Duration.ofMillis(10));
    properties.getRetry().setMaxBackoff(Duration.ofMillis(25));

    mockCrumb = mock(YahooCrumbProvider.class);
    when(mockCrumb.getCrumb()).thenReturn("test-crumb");
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  // ── getQuote ────────────────────────────────────────────────────────────────

  @Test
  void getQuoteParsesResponseCorrectly() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "quoteResponse": {
                    "result": [{
                      "symbol": "AAPL",
                      "regularMarketPrice": 178.72,
                      "regularMarketChangePercent": 1.23,
                      "regularMarketTime": 1709900400
                    }],
                    "error": null
                  }
                }
                """));

    Quote quote = provider().getQuote("AAPL");

    assertThat(quote.symbol()).isEqualTo("AAPL");
    assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("178.72"));
    assertThat(quote.changePercent()).isEqualByComparingTo(new BigDecimal("1.23"));
  }

  @Test
  void getQuoteThrowsOnEmptyResultArray() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"quoteResponse": {"result": [], "error": null}}
                """));

    assertThatThrownBy(() -> provider().getQuote("INVALID"))
        .isInstanceOf(QuoteNotFoundException.class);
  }

  @Test
  void getQuoteThrowsRateLimitOn429() {
    server.enqueue(new MockResponse().setResponseCode(429).setBody("{}"));

    assertThatThrownBy(() -> provider().getQuote("AAPL"))
        .isInstanceOf(MarketDataRateLimitException.class);
  }

  // ── getDailyHistory ─────────────────────────────────────────────────────────

  @Test
  void getDailyHistoryParsesTwoBars() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "chart": {
                    "result": [{
                      "timestamp": [1690848000, 1690934400],
                      "indicators": {
                        "quote": [{
                          "open":  [149.50, 151.00],
                          "high":  [151.00, 153.00],
                          "low":   [149.00, 151.50],
                          "close": [150.10, 152.50],
                          "volume":[34000000, 28000000]
                        }]
                      }
                    }],
                    "error": null
                  }
                }
                """));

    List<DailyBar> bars = provider().getDailyHistory("AAPL");

    assertThat(bars).hasSize(2);
    assertThat(bars.get(0).close()).isEqualByComparingTo(new BigDecimal("150.10"));
    assertThat(bars.get(1).close()).isEqualByComparingTo(new BigDecimal("152.50"));
    assertThat(bars.get(0).date()).isBefore(bars.get(1).date());
  }

  @Test
  void getDailyHistoryThrowsOnMissingResult() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"chart": {"result": null, "error": null}}
                """));

    assertThatThrownBy(() -> provider().getDailyHistory("INVALID"))
        .isInstanceOf(QuoteNotFoundException.class);
  }

  // ── getOverview ─────────────────────────────────────────────────────────────

  @Test
  void getOverviewParsesAllFields() {
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
                        "shortName": "Apple Inc.",
                        "marketCap": {"raw": 2800000000000.0}
                      },
                      "summaryProfile": {"sector": "Technology"},
                      "summaryDetail": {
                        "trailingPE":    {"raw": 28.5},
                        "dividendYield": {"raw": 0.006}
                      },
                      "defaultKeyStatistics": {
                        "beta":           {"raw": 1.23},
                        "trailingEps":    {"raw": 6.05},
                        "fiftyTwoWeekHigh": {"raw": 198.23},
                        "fiftyTwoWeekLow":  {"raw": 124.17}
                      }
                    }],
                    "error": null
                  }
                }
                """));

    CompanyOverview overview = provider().getOverview("AAPL");

    assertThat(overview.name()).isEqualTo("Apple Inc.");
    assertThat(overview.sector()).isEqualTo("Technology");
    assertThat(overview.marketCap()).isEqualByComparingTo(new BigDecimal("2800000000000.0"));
    assertThat(overview.pe()).isEqualByComparingTo(new BigDecimal("28.5"));
    assertThat(overview.dividendYield()).isEqualByComparingTo(new BigDecimal("0.006"));
    assertThat(overview.beta()).isEqualByComparingTo(new BigDecimal("1.23"));
    assertThat(overview.eps()).isEqualByComparingTo(new BigDecimal("6.05"));
    assertThat(overview.fiftyTwoWeekHigh()).isEqualByComparingTo(new BigDecimal("198.23"));
    assertThat(overview.fiftyTwoWeekLow()).isEqualByComparingTo(new BigDecimal("124.17"));
  }

  @Test
  void getOverviewInvalidatesCrumbAndRetriesOn401() {
    // First call → 401
    server.enqueue(new MockResponse().setResponseCode(401).setBody("Unauthorized"));
    // Retry after crumb refresh → 200
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "quoteSummary": {
                    "result": [{
                      "price": {"shortName": "Apple Inc.", "marketCap": {"raw": 2800000000000.0}},
                      "summaryProfile": {"sector": "Technology"},
                      "summaryDetail": {},
                      "defaultKeyStatistics": {}
                    }],
                    "error": null
                  }
                }
                """));

    when(mockCrumb.getCrumb()).thenReturn("crumb-v1").thenReturn("crumb-v2");

    CompanyOverview overview = provider().getOverview("AAPL");

    assertThat(overview.name()).isEqualTo("Apple Inc.");
    verify(mockCrumb).invalidateCrumb();
    verify(mockCrumb, times(2)).getCrumb();
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  // ── getNews ─────────────────────────────────────────────────────────────────

  @Test
  void getNewsParsesRssFeed() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/rss+xml")
            .setBody(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Yahoo Finance</title>
                    <item>
                      <title>Apple announces new product</title>
                      <link>https://finance.yahoo.com/news/apple-1</link>
                      <description>Apple Inc announced a new product line.</description>
                      <pubDate>Thu, 14 Mar 2024 12:00:00 GMT</pubDate>
                      <guid>urn:apple-article-guid-1</guid>
                    </item>
                  </channel>
                </rss>
                """));

    List<NewsArticle> articles = provider().getNews("AAPL");

    assertThat(articles).hasSize(1);
    assertThat(articles.get(0).headline()).isEqualTo("Apple announces new product");
    assertThat(articles.get(0).url()).isEqualTo("https://finance.yahoo.com/news/apple-1");
    assertThat(articles.get(0).image()).isNull();
    long expectedId = (long) Math.abs(Objects.hashCode("urn:apple-article-guid-1"));
    assertThat(articles.get(0).id()).isEqualTo(expectedId);
  }

  private YahooFinanceMarketDataProvider provider(String... activeProfiles) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(activeProfiles);
    return new YahooFinanceMarketDataProvider(
        WebClient.builder(), properties, new MarketDataQuotaTracker(), mockCrumb, env);
  }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew test --tests "*.YahooFinanceMarketDataProviderTest"
```
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement YahooFinanceMarketDataProvider**

Create `YahooFinanceMarketDataProvider.java`:

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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;
import org.xml.sax.InputSource;

@Component
@Profile("!dev")
public class YahooFinanceMarketDataProvider implements MarketDataProvider {

  private static final Logger log = LoggerFactory.getLogger(YahooFinanceMarketDataProvider.class);
  private static final String USER_AGENT = "Mozilla/5.0 (compatible; trading-dashboard/1.0)";
  private static final Set<String> NULL_MARKERS = Set.of("none", "-", "null", "");

  private final WebClient query1Client;
  private final WebClient query2Client;
  private final WebClient rssClient;
  private final YahooCrumbProvider crumbProvider;
  private final MarketDataQuotaTracker quotaTracker;
  private final MarketDataProperties properties;
  private final boolean retryEnabled;
  private final int maxAttempts;
  private final RetryBackoffSpec baseRetrySpec;

  public YahooFinanceMarketDataProvider(
      WebClient.Builder builder,
      MarketDataProperties properties,
      MarketDataQuotaTracker quotaTracker,
      YahooCrumbProvider crumbProvider,
      Environment environment) {
    this.properties = Objects.requireNonNull(properties);
    this.quotaTracker = Objects.requireNonNull(quotaTracker);
    this.crumbProvider = Objects.requireNonNull(crumbProvider);

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
    ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

    this.query1Client =
        builder
            .baseUrl(normalizeUrl(properties.getBaseUrl()))
            .clientConnector(connector)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
            .build();

    this.query2Client =
        WebClient.builder()
            .baseUrl(normalizeUrl(properties.getQuery2BaseUrl()))
            .clientConnector(connector)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
            .build();

    this.rssClient =
        WebClient.builder()
            .baseUrl(normalizeUrl(properties.getYahooRssBaseUrl()))
            .clientConnector(connector)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
            .build();

    this.retryEnabled =
        Arrays.stream(environment.getActiveProfiles())
            .anyMatch(p -> p.equalsIgnoreCase("prod"));
    this.maxAttempts = Math.max(1, properties.getRetry().getMaxAttempts());
    this.baseRetrySpec =
        maxAttempts > 1
            ? Retry.backoff(maxAttempts - 1, properties.getRetry().getInitialBackoff())
                .maxBackoff(properties.getRetry().getMaxBackoff())
                .filter(
                    t ->
                        t instanceof MarketDataClientException
                            && !(t instanceof MarketDataRateLimitException))
                .onRetryExhaustedThrow((spec, signal) -> propagateFinalFailure(signal.failure()))
            : null;
  }

  @Override
  public Quote getQuote(String symbol) {
    requireSymbol(symbol);
    quotaTracker.increment();
    JsonNode root =
        withRetry(fetchJson(query1Client, "/v7/finance/quote", symbol), "quote", symbol)
            .block(properties.getReadTimeout());
    return toQuote(symbol, root);
  }

  @Override
  public List<DailyBar> getDailyHistory(String symbol) {
    requireSymbol(symbol);
    quotaTracker.increment();
    JsonNode root =
        withRetry(
                query1Client
                    .get()
                    .uri(
                        u ->
                            u.path("/v8/finance/chart/{symbol}")
                                .queryParam("range", "3mo")
                                .queryParam("interval", "1d")
                                .build(symbol))
                    .retrieve()
                    .onStatus(
                        s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(b -> new MarketDataRateLimitException("Yahoo Finance rate limit reached")))
                    .onStatus(
                        HttpStatusCode::isError,
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(b -> new MarketDataClientException("Yahoo Finance error " + r.statusCode() + ": " + b)))
                    .bodyToMono(JsonNode.class)
                    .onErrorMap(
                        WebClientResponseException.class,
                        ex -> new MarketDataClientException("Yahoo Finance call failed: " + ex.getStatusCode(), ex))
                    .onErrorMap(
                        WebClientRequestException.class,
                        ex -> new MarketDataClientException("Yahoo Finance request failed: " + ex.getMessage(), ex)),
                "history",
                symbol)
            .block(properties.getReadTimeout());
    return toHistory(symbol, root);
  }

  @Override
  public CompanyOverview getOverview(String symbol) {
    requireSymbol(symbol);
    quotaTracker.increment();
    try {
      return fetchOverviewOnce(symbol, crumbProvider.getCrumb());
    } catch (MarketDataClientException ex) {
      if (ex.getMessage() != null && ex.getMessage().contains("Yahoo Finance 401")) {
        log.warn("quoteSummary returned 401 for {}; invalidating crumb and retrying", symbol);
        crumbProvider.invalidateCrumb();
        return fetchOverviewOnce(symbol, crumbProvider.getCrumb());
      }
      throw ex;
    }
  }

  @Override
  public List<NewsArticle> getNews(String symbol) {
    requireSymbol(symbol);
    quotaTracker.increment();
    String xml =
        rssClient
            .get()
            .uri(u -> u.path("/rss/headline").queryParam("s", symbol).build())
            .retrieve()
            .onStatus(HttpStatusCode::isError, r -> Mono.empty())
            .bodyToMono(String.class)
            .onErrorReturn("")
            .block(properties.getReadTimeout());
    return parseRss(xml);
  }

  // ── Private helpers ─────────────────────────────────────────────────────────

  private CompanyOverview fetchOverviewOnce(String symbol, String crumb) {
    JsonNode root =
        query2Client
            .get()
            .uri(
                u ->
                    u.path("/v10/finance/quoteSummary/{symbol}")
                        .queryParam("crumb", crumb)
                        .queryParam(
                            "modules", "price,summaryProfile,summaryDetail,defaultKeyStatistics")
                        .build(symbol))
            .retrieve()
            .onStatus(
                s -> s.value() == HttpStatus.UNAUTHORIZED.value(),
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new MarketDataClientException("Yahoo Finance 401 on quoteSummary for " + symbol)))
            .onStatus(
                s -> s.value() == HttpStatus.NOT_FOUND.value(),
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new QuoteNotFoundException("Overview not found for " + symbol)))
            .onStatus(
                s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new MarketDataRateLimitException("Yahoo Finance rate limit reached")))
            .onStatus(
                HttpStatusCode::isError,
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new MarketDataClientException("Yahoo Finance error " + r.statusCode() + ": " + b)))
            .bodyToMono(JsonNode.class)
            .block(properties.getReadTimeout());
    return toOverview(symbol, root);
  }

  private Mono<JsonNode> fetchJson(WebClient client, String path, String symbol) {
    return client
        .get()
        .uri(u -> u.path(path).queryParam("symbols", symbol).build())
        .retrieve()
        .onStatus(
            s -> s.value() == HttpStatus.NOT_FOUND.value(),
            r ->
                r.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(b -> new QuoteNotFoundException("Quote not found for " + symbol)))
        .onStatus(
            s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
            r ->
                r.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(b -> new MarketDataRateLimitException("Yahoo Finance rate limit reached")))
        .onStatus(
            HttpStatusCode::isError,
            r ->
                r.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(b -> new MarketDataClientException("Yahoo Finance error " + r.statusCode() + ": " + b)))
        .bodyToMono(JsonNode.class)
        .onErrorMap(
            WebClientResponseException.class,
            ex -> new MarketDataClientException("Yahoo Finance call failed: " + ex.getStatusCode(), ex))
        .onErrorMap(
            WebClientRequestException.class,
            ex -> new MarketDataClientException("Yahoo Finance request failed: " + ex.getMessage(), ex));
  }

  private <T> Mono<T> withRetry(Mono<T> mono, String action, String symbol) {
    if (!retryEnabled || baseRetrySpec == null) return mono;
    return mono.retryWhen(
        baseRetrySpec.doBeforeRetry(
            s ->
                log.warn(
                    "Retrying Yahoo Finance {} for {} after attempt {}: {}",
                    action,
                    symbol,
                    s.totalRetries(),
                    s.failure().getMessage())));
  }

  private RuntimeException propagateFinalFailure(Throwable failure) {
    if (failure instanceof RuntimeException r) return r;
    return new MarketDataClientException("Yahoo Finance retries exhausted", failure);
  }

  // ── Parsing ─────────────────────────────────────────────────────────────────

  private Quote toQuote(String symbol, JsonNode root) {
    JsonNode result = root == null ? null : root.path("quoteResponse").path("result");
    if (result == null || !result.isArray() || result.isEmpty()) {
      throw new QuoteNotFoundException("Quote not found for " + symbol);
    }
    JsonNode item = result.get(0);
    JsonNode priceNode = item.path("regularMarketPrice");
    if (priceNode.isMissingNode() || priceNode.isNull() || priceNode.asDouble(0) == 0) {
      throw new QuoteNotFoundException("Quote not found for " + symbol);
    }
    BigDecimal price = BigDecimal.valueOf(priceNode.asDouble());
    JsonNode cpNode = item.path("regularMarketChangePercent");
    BigDecimal changePercent =
        (!cpNode.isMissingNode() && !cpNode.isNull())
            ? BigDecimal.valueOf(cpNode.asDouble())
            : null;
    long epochSeconds = item.path("regularMarketTime").asLong(0);
    Instant timestamp = epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : Instant.now();
    return new Quote(symbol, price, changePercent, timestamp);
  }

  private List<DailyBar> toHistory(String symbol, JsonNode root) {
    JsonNode result = root == null ? null : root.path("chart").path("result");
    if (result == null || !result.isArray() || result.isEmpty()) {
      throw new QuoteNotFoundException("History not found for " + symbol);
    }
    JsonNode item = result.get(0);
    JsonNode timestamps = item.path("timestamp");
    JsonNode quoteArr = item.path("indicators").path("quote");
    if (!timestamps.isArray() || timestamps.isEmpty() || !quoteArr.isArray() || quoteArr.isEmpty()) {
      throw new QuoteNotFoundException("History not found for " + symbol);
    }
    JsonNode q = quoteArr.get(0);
    List<DailyBar> bars = new ArrayList<>();
    for (int i = 0; i < timestamps.size(); i++) {
      try {
        LocalDate date =
            Instant.ofEpochSecond(timestamps.get(i).asLong()).atZone(ZoneOffset.UTC).toLocalDate();
        bars.add(
            new DailyBar(
                date,
                bigDecimalAt(q.path("open"), i),
                bigDecimalAt(q.path("high"), i),
                bigDecimalAt(q.path("low"), i),
                bigDecimalAt(q.path("close"), i),
                q.path("volume").has(i) ? q.path("volume").get(i).asLong(0) : 0L));
      } catch (Exception ex) {
        log.warn("Skipping malformed candle at index {}", i, ex);
      }
    }
    bars.sort(Comparator.comparing(DailyBar::date));
    return bars;
  }

  private CompanyOverview toOverview(String symbol, JsonNode root) {
    JsonNode result = root == null ? null : root.path("quoteSummary").path("result");
    if (result == null || !result.isArray() || result.isEmpty()) {
      throw new QuoteNotFoundException("Overview not found for " + symbol);
    }
    JsonNode item = result.get(0);
    JsonNode price = item.path("price");
    JsonNode profile = item.path("summaryProfile");
    JsonNode detail = item.path("summaryDetail");
    JsonNode stats = item.path("defaultKeyStatistics");

    String name = safeText(price, "shortName");
    if (name == null) name = safeText(price, "longName");
    String sector = safeText(profile, "sector");
    BigDecimal marketCap = safeRaw(price, "marketCap");
    BigDecimal pe = safeRaw(detail, "trailingPE");
    BigDecimal dividendYield = safeRaw(detail, "dividendYield");
    BigDecimal beta = safeRaw(stats, "beta");
    BigDecimal eps = safeRaw(stats, "trailingEps");
    BigDecimal high52 = safeRaw(stats, "fiftyTwoWeekHigh");
    BigDecimal low52 = safeRaw(stats, "fiftyTwoWeekLow");
    return new CompanyOverview(symbol, name, sector, null, marketCap, pe, eps, dividendYield, beta, high52, low52);
  }

  private List<NewsArticle> parseRss(String xml) {
    if (xml == null || xml.isBlank()) return List.of();
    try {
      SyndFeed feed = new SyndFeedInput().build(new InputSource(new StringReader(xml)));
      List<NewsArticle> articles = new ArrayList<>();
      for (SyndEntry e : feed.getEntries()) {
        try {
          String headline = e.getTitle();
          String url = e.getLink();
          if (headline == null || url == null) continue;
          String summary = e.getDescription() != null ? e.getDescription().getValue() : null;
          String source = e.getSource() != null ? e.getSource().getTitle() : null;
          Instant publishedAt =
              e.getPublishedDate() != null ? e.getPublishedDate().toInstant() : Instant.now();
          long id = (long) Math.abs(Objects.hashCode(e.getUri()));
          articles.add(new NewsArticle(id, headline, summary, source, url, null, publishedAt));
        } catch (Exception ex) {
          log.warn("Skipping malformed RSS entry", ex);
        }
      }
      return articles.stream()
          .sorted(Comparator.comparing(NewsArticle::publishedAt).reversed())
          .limit(10)
          .toList();
    } catch (Exception ex) {
      log.warn("Failed to parse RSS feed: {}", ex.getMessage());
      return List.of();
    }
  }

  private static BigDecimal bigDecimalAt(JsonNode arr, int i) {
    if (!arr.isArray() || !arr.has(i) || arr.get(i).isNull()) return BigDecimal.ZERO;
    return BigDecimal.valueOf(arr.get(i).asDouble());
  }

  private static BigDecimal safeRaw(JsonNode parent, String field) {
    JsonNode node = parent.path(field);
    if (node.isMissingNode() || node.isNull()) return null;
    JsonNode raw = node.path("raw");
    if (raw.isMissingNode() || raw.isNull()) return null;
    return raw.isNumber() ? BigDecimal.valueOf(raw.asDouble()) : null;
  }

  private static String safeText(JsonNode node, String field) {
    if (node == null || node.isMissingNode()) return null;
    JsonNode raw = node.get(field);
    if (raw == null || raw.isNull()) return null;
    String val = raw.asText(null);
    if (val == null) return null;
    val = val.trim();
    return NULL_MARKERS.contains(val.toLowerCase(java.util.Locale.ROOT)) ? null : val;
  }

  private static void requireSymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
  }

  private static String normalizeUrl(String url) {
    if (url == null) throw new MarketDataClientException("URL must not be null");
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
```

- [ ] **Step 4: Run spotlessApply, then run tests**

```bash
./gradlew spotlessApply
./gradlew test --tests "*.YahooFinanceMarketDataProviderTest"
```
Expected: 8 tests, all PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/austinharlan/trading_dashboard/marketdata/YahooFinanceMarketDataProvider.java \
  src/test/java/com/austinharlan/trading_dashboard/marketdata/YahooFinanceMarketDataProviderTest.java
git commit -m "feat: add YahooFinanceMarketDataProvider for stocks, ETFs, indices, options"
```

---

### Task 5: CoinGeckoMarketDataProvider

**Files:**
- Create: `src/main/java/com/austinharlan/trading_dashboard/marketdata/CoinGeckoMarketDataProvider.java`
- Create: `src/test/java/com/austinharlan/trading_dashboard/marketdata/CoinGeckoMarketDataProviderTest.java`

- [ ] **Step 1: Write the failing tests**

Create `CoinGeckoMarketDataProviderTest.java`:

```java
package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

class CoinGeckoMarketDataProviderTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private MockWebServer server;
  private MarketDataProperties properties;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    String url = server.url("/").toString();
    properties = new MarketDataProperties();
    properties.setBaseUrl(url);
    properties.setQuery2BaseUrl(url);
    properties.setYahooRssBaseUrl(url);
    properties.setCoinGeckoBaseUrl(url);
    properties.setCryptoSymbols(Map.of("BTC", "bitcoin", "ETH", "ethereum"));
    properties.setConnectTimeout(TIMEOUT);
    properties.setReadTimeout(TIMEOUT);
    properties.setWriteTimeout(TIMEOUT);
    properties.getRetry().setInitialBackoff(Duration.ofMillis(10));
    properties.getRetry().setMaxBackoff(Duration.ofMillis(25));
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void getQuoteParsesResponse() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "bitcoin": {
                    "usd": 42000.0,
                    "usd_24h_change": 1.5,
                    "usd_last_updated_at": 1709900400
                  }
                }
                """));

    Quote quote = provider().getQuote("bitcoin");

    assertThat(quote.symbol()).isEqualTo("bitcoin");
    assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("42000.0"));
    assertThat(quote.changePercent()).isEqualByComparingTo(new BigDecimal("1.5"));
  }

  @Test
  void getQuoteThrowsOnEmptyResponse() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{}"));

    assertThatThrownBy(() -> provider().getQuote("unknowncoin"))
        .isInstanceOf(QuoteNotFoundException.class);
  }

  @Test
  void getQuoteThrowsRateLimitOn429() {
    server.enqueue(new MockResponse().setResponseCode(429).setBody("{}"));

    assertThatThrownBy(() -> provider().getQuote("bitcoin"))
        .isInstanceOf(MarketDataRateLimitException.class);
  }

  @Test
  void getDailyHistoryParsesOhlcTimestampFromMs() {
    // CoinGecko timestamps are in milliseconds
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                [
                  [1690848000000, 29000.0, 29500.0, 28800.0, 29200.0],
                  [1690934400000, 29200.0, 29800.0, 29100.0, 29600.0]
                ]
                """));

    List<DailyBar> bars = provider().getDailyHistory("bitcoin");

    assertThat(bars).hasSize(2);
    assertThat(bars.get(0).close()).isEqualByComparingTo(new BigDecimal("29200.0"));
    assertThat(bars.get(1).close()).isEqualByComparingTo(new BigDecimal("29600.0"));
    assertThat(bars.get(0).date()).isBefore(bars.get(1).date());
  }

  @Test
  void getOverviewParsesFields() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "name": "Bitcoin",
                  "market_data": {
                    "market_cap": {"usd": 820000000000.0},
                    "ath":        {"usd": 69000.0},
                    "atl":        {"usd": 67.81}
                  }
                }
                """));

    CompanyOverview overview = provider().getOverview("bitcoin");

    assertThat(overview.name()).isEqualTo("Bitcoin");
    assertThat(overview.sector()).isEqualTo("Cryptocurrency");
    assertThat(overview.marketCap()).isEqualByComparingTo(new BigDecimal("820000000000.0"));
    assertThat(overview.fiftyTwoWeekHigh()).isEqualByComparingTo(new BigDecimal("69000.0"));
    assertThat(overview.pe()).isNull();
    assertThat(overview.eps()).isNull();
    assertThat(overview.dividendYield()).isNull();
  }

  @Test
  void getNewsFetchesYahooRssWithDashUsdSuffix() throws InterruptedException {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/rss+xml")
            .setBody(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Yahoo Finance</title>
                    <item>
                      <title>Bitcoin surges past $42K</title>
                      <link>https://finance.yahoo.com/news/btc-1</link>
                      <description>Bitcoin hit new highs.</description>
                      <pubDate>Thu, 14 Mar 2024 12:00:00 GMT</pubDate>
                      <guid>urn:btc-article-1</guid>
                    </item>
                  </channel>
                </rss>
                """));

    CryptoSymbolMapper mapper = mapper();
    List<NewsArticle> articles = provider(mapper).getNews("BTC");

    assertThat(articles).hasSize(1);
    assertThat(articles.get(0).headline()).isEqualTo("Bitcoin surges past $42K");

    // Verify the RSS URL used BTC-USD
    String path = server.takeRequest().getPath();
    assertThat(path).contains("BTC-USD");
  }

  private CoinGeckoMarketDataProvider provider() {
    return provider(mapper());
  }

  private CoinGeckoMarketDataProvider provider(CryptoSymbolMapper mapper) {
    return new CoinGeckoMarketDataProvider(
        WebClient.builder(), properties, new MarketDataQuotaTracker(), mapper, new MockEnvironment());
  }

  private CryptoSymbolMapper mapper() {
    return new CryptoSymbolMapper(properties);
  }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew test --tests "*.CoinGeckoMarketDataProviderTest"
```
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement CoinGeckoMarketDataProvider**

Create `CoinGeckoMarketDataProvider.java`:

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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;
import org.xml.sax.InputSource;

@Component
@Profile("!dev")
public class CoinGeckoMarketDataProvider implements MarketDataProvider {

  private static final Logger log = LoggerFactory.getLogger(CoinGeckoMarketDataProvider.class);
  private static final String USER_AGENT = "Mozilla/5.0 (compatible; trading-dashboard/1.0)";

  private final WebClient coinGeckoClient;
  private final WebClient rssClient;
  private final CryptoSymbolMapper mapper;
  private final MarketDataQuotaTracker quotaTracker;
  private final MarketDataProperties properties;
  private final boolean retryEnabled;
  private final int maxAttempts;
  private final RetryBackoffSpec baseRetrySpec;

  public CoinGeckoMarketDataProvider(
      WebClient.Builder builder,
      MarketDataProperties properties,
      MarketDataQuotaTracker quotaTracker,
      CryptoSymbolMapper mapper,
      Environment environment) {
    this.properties = Objects.requireNonNull(properties);
    this.quotaTracker = Objects.requireNonNull(quotaTracker);
    this.mapper = Objects.requireNonNull(mapper);

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
    ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

    this.coinGeckoClient =
        builder
            .baseUrl(normalizeUrl(properties.getCoinGeckoBaseUrl()))
            .clientConnector(connector)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();

    this.rssClient =
        WebClient.builder()
            .baseUrl(normalizeUrl(properties.getYahooRssBaseUrl()))
            .clientConnector(connector)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
            .build();

    this.retryEnabled =
        Arrays.stream(environment.getActiveProfiles())
            .anyMatch(p -> p.equalsIgnoreCase("prod"));
    this.maxAttempts = Math.max(1, properties.getRetry().getMaxAttempts());
    this.baseRetrySpec =
        maxAttempts > 1
            ? Retry.backoff(maxAttempts - 1, properties.getRetry().getInitialBackoff())
                .maxBackoff(properties.getRetry().getMaxBackoff())
                .filter(
                    t ->
                        t instanceof MarketDataClientException
                            && !(t instanceof MarketDataRateLimitException))
                .onRetryExhaustedThrow((spec, signal) -> propagateFinalFailure(signal.failure()))
            : null;
  }

  @Override
  public Quote getQuote(String coinId) {
    requireSymbol(coinId);
    quotaTracker.increment();
    JsonNode root =
        withRetry(
                coinGeckoClient
                    .get()
                    .uri(
                        u ->
                            u.path("/api/v3/simple/price")
                                .queryParam("ids", coinId)
                                .queryParam("vs_currencies", "usd")
                                .queryParam("include_24hr_change", "true")
                                .queryParam("include_last_updated_at", "true")
                                .build())
                    .retrieve()
                    .onStatus(
                        s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(b -> new MarketDataRateLimitException("CoinGecko rate limit reached")))
                    .onStatus(
                        HttpStatusCode::isError,
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(b -> new MarketDataClientException("CoinGecko error " + r.statusCode() + ": " + b)))
                    .bodyToMono(JsonNode.class)
                    .onErrorMap(
                        WebClientResponseException.class,
                        ex -> new MarketDataClientException("CoinGecko call failed: " + ex.getStatusCode(), ex))
                    .onErrorMap(
                        WebClientRequestException.class,
                        ex -> new MarketDataClientException("CoinGecko request failed: " + ex.getMessage(), ex)),
                "quote",
                coinId)
            .block(properties.getReadTimeout());
    return toQuote(coinId, root);
  }

  @Override
  public List<DailyBar> getDailyHistory(String coinId) {
    requireSymbol(coinId);
    quotaTracker.increment();
    JsonNode root =
        withRetry(
                coinGeckoClient
                    .get()
                    .uri(
                        u ->
                            u.path("/api/v3/coins/{id}/ohlc")
                                .queryParam("vs_currency", "usd")
                                .queryParam("days", "90")
                                .build(coinId))
                    .retrieve()
                    .onStatus(
                        s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(b -> new MarketDataRateLimitException("CoinGecko rate limit reached")))
                    .onStatus(
                        HttpStatusCode::isError,
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(b -> new MarketDataClientException("CoinGecko error " + r.statusCode() + ": " + b)))
                    .bodyToMono(JsonNode.class),
                "history",
                coinId)
            .block(properties.getReadTimeout());
    return toHistory(coinId, root);
  }

  @Override
  public CompanyOverview getOverview(String coinId) {
    requireSymbol(coinId);
    quotaTracker.increment();
    JsonNode root =
        withRetry(
                coinGeckoClient
                    .get()
                    .uri(
                        u ->
                            u.path("/api/v3/coins/{id}")
                                .queryParam("localization", "false")
                                .queryParam("tickers", "false")
                                .queryParam("market_data", "true")
                                .queryParam("community_data", "false")
                                .queryParam("developer_data", "false")
                                .build(coinId))
                    .retrieve()
                    .onStatus(
                        s -> s.value() == HttpStatus.NOT_FOUND.value(),
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(b -> new QuoteNotFoundException("Overview not found for " + coinId)))
                    .onStatus(
                        s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(b -> new MarketDataRateLimitException("CoinGecko rate limit reached")))
                    .onStatus(
                        HttpStatusCode::isError,
                        r ->
                            r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(b -> new MarketDataClientException("CoinGecko error " + r.statusCode() + ": " + b)))
                    .bodyToMono(JsonNode.class),
                "overview",
                coinId)
            .block(properties.getReadTimeout());
    return toOverview(coinId, root);
  }

  @Override
  public List<NewsArticle> getNews(String tickerSymbol) {
    requireSymbol(tickerSymbol);
    quotaTracker.increment();
    String rssSymbol = mapper.toYahooRssSymbol(tickerSymbol);
    String xml =
        rssClient
            .get()
            .uri(u -> u.path("/rss/headline").queryParam("s", rssSymbol).build())
            .retrieve()
            .onStatus(HttpStatusCode::isError, r -> Mono.empty())
            .bodyToMono(String.class)
            .onErrorReturn("")
            .block(properties.getReadTimeout());
    return parseRss(xml);
  }

  // ── Parsing ─────────────────────────────────────────────────────────────────

  private Quote toQuote(String coinId, JsonNode root) {
    if (root == null || !root.has(coinId)) {
      throw new QuoteNotFoundException("Quote not found for " + coinId);
    }
    JsonNode data = root.path(coinId);
    if (data.isEmpty()) throw new QuoteNotFoundException("Quote not found for " + coinId);
    double price = data.path("usd").asDouble(0);
    if (price == 0) throw new QuoteNotFoundException("Quote not found for " + coinId);
    BigDecimal changePercent =
        data.has("usd_24h_change")
            ? BigDecimal.valueOf(data.path("usd_24h_change").asDouble())
            : null;
    long epochSeconds = data.path("usd_last_updated_at").asLong(0);
    Instant timestamp = epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : Instant.now();
    return new Quote(coinId, BigDecimal.valueOf(price), changePercent, timestamp);
  }

  private List<DailyBar> toHistory(String coinId, JsonNode root) {
    if (root == null || !root.isArray() || root.isEmpty()) {
      throw new QuoteNotFoundException("History not found for " + coinId);
    }
    List<DailyBar> bars = new ArrayList<>();
    for (JsonNode row : root) {
      try {
        // [timestamp_ms, open, high, low, close]
        long timestampMs = row.get(0).asLong();
        LocalDate date =
            Instant.ofEpochMilli(timestampMs).atZone(ZoneOffset.UTC).toLocalDate();
        BigDecimal open  = BigDecimal.valueOf(row.get(1).asDouble());
        BigDecimal high  = BigDecimal.valueOf(row.get(2).asDouble());
        BigDecimal low   = BigDecimal.valueOf(row.get(3).asDouble());
        BigDecimal close = BigDecimal.valueOf(row.get(4).asDouble());
        bars.add(new DailyBar(date, open, high, low, close, 0L));
      } catch (Exception ex) {
        log.warn("Skipping malformed OHLC row", ex);
      }
    }
    bars.sort(Comparator.comparing(DailyBar::date));
    return bars;
  }

  private CompanyOverview toOverview(String coinId, JsonNode root) {
    if (root == null || root.isEmpty()) {
      throw new QuoteNotFoundException("Overview not found for " + coinId);
    }
    String name = root.path("name").asText(null);
    JsonNode marketData = root.path("market_data");
    BigDecimal marketCap =
        marketData.path("market_cap").has("usd")
            ? BigDecimal.valueOf(marketData.path("market_cap").path("usd").asDouble())
            : null;
    BigDecimal ath =
        marketData.path("ath").has("usd")
            ? BigDecimal.valueOf(marketData.path("ath").path("usd").asDouble())
            : null;
    BigDecimal atl =
        marketData.path("atl").has("usd")
            ? BigDecimal.valueOf(marketData.path("atl").path("usd").asDouble())
            : null;
    return new CompanyOverview(coinId, name, "Cryptocurrency", null, marketCap, null, null, null, null, ath, atl);
  }

  private List<NewsArticle> parseRss(String xml) {
    if (xml == null || xml.isBlank()) return List.of();
    try {
      SyndFeed feed = new SyndFeedInput().build(new InputSource(new StringReader(xml)));
      List<NewsArticle> articles = new ArrayList<>();
      for (SyndEntry e : feed.getEntries()) {
        try {
          String headline = e.getTitle();
          String url = e.getLink();
          if (headline == null || url == null) continue;
          String summary = e.getDescription() != null ? e.getDescription().getValue() : null;
          String source = e.getSource() != null ? e.getSource().getTitle() : null;
          Instant publishedAt =
              e.getPublishedDate() != null ? e.getPublishedDate().toInstant() : Instant.now();
          long id = (long) Math.abs(Objects.hashCode(e.getUri()));
          articles.add(new NewsArticle(id, headline, summary, source, url, null, publishedAt));
        } catch (Exception ex) {
          log.warn("Skipping malformed RSS entry", ex);
        }
      }
      return articles.stream()
          .sorted(Comparator.comparing(NewsArticle::publishedAt).reversed())
          .limit(10)
          .toList();
    } catch (Exception ex) {
      log.warn("Failed to parse RSS feed: {}", ex.getMessage());
      return List.of();
    }
  }

  private <T> Mono<T> withRetry(Mono<T> mono, String action, String coinId) {
    if (!retryEnabled || baseRetrySpec == null) return mono;
    return mono.retryWhen(
        baseRetrySpec.doBeforeRetry(
            s ->
                log.warn(
                    "Retrying CoinGecko {} for {} after attempt {}: {}",
                    action,
                    coinId,
                    s.totalRetries(),
                    s.failure().getMessage())));
  }

  private RuntimeException propagateFinalFailure(Throwable failure) {
    if (failure instanceof RuntimeException r) return r;
    return new MarketDataClientException("CoinGecko retries exhausted", failure);
  }

  private static void requireSymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
  }

  private static String normalizeUrl(String url) {
    if (url == null) throw new MarketDataClientException("URL must not be null");
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
```

- [ ] **Step 4: Run spotlessApply, then run tests**

```bash
./gradlew spotlessApply
./gradlew test --tests "*.CoinGeckoMarketDataProviderTest"
```
Expected: 6 tests, all PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/austinharlan/trading_dashboard/marketdata/CoinGeckoMarketDataProvider.java \
  src/test/java/com/austinharlan/trading_dashboard/marketdata/CoinGeckoMarketDataProviderTest.java
git commit -m "feat: add CoinGeckoMarketDataProvider for BTC/ETH/SOL/XRP"
```

---

### Task 6: RoutingMarketDataProvider

**Files:**
- Create: `src/main/java/com/austinharlan/trading_dashboard/marketdata/RoutingMarketDataProvider.java`
- Create: `src/test/java/com/austinharlan/trading_dashboard/marketdata/RoutingMarketDataProviderTest.java`

- [ ] **Step 1: Write the failing tests**

Create `RoutingMarketDataProviderTest.java`:

```java
package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoutingMarketDataProviderTest {

  private YahooFinanceMarketDataProvider yahoo;
  private CoinGeckoMarketDataProvider coinGecko;
  private RoutingMarketDataProvider router;

  @BeforeEach
  void setUp() {
    yahoo = mock(YahooFinanceMarketDataProvider.class);
    coinGecko = mock(CoinGeckoMarketDataProvider.class);

    MarketDataProperties props = new MarketDataProperties();
    props.setBaseUrl("https://query1.finance.yahoo.com");
    props.setQuery2BaseUrl("https://query2.finance.yahoo.com");
    props.setYahooRssBaseUrl("https://finance.yahoo.com");
    props.setCoinGeckoBaseUrl("https://api.coingecko.com");
    props.setCryptoSymbols(
        Map.of("BTC", "bitcoin", "ETH", "ethereum", "SOL", "solana", "XRP", "ripple"));

    CryptoSymbolMapper mapper = new CryptoSymbolMapper(props);
    router = new RoutingMarketDataProvider(yahoo, coinGecko, mapper);
  }

  @Test
  void btcQuoteRoutes_toCoinGeckoWithCoinGeckoId() {
    Quote expected = new Quote("bitcoin", BigDecimal.valueOf(42000), null, Instant.now());
    when(coinGecko.getQuote("bitcoin")).thenReturn(expected);

    Quote result = router.getQuote("BTC");

    assertThat(result).isEqualTo(expected);
    verify(coinGecko).getQuote("bitcoin");
    verifyNoInteractions(yahoo);
  }

  @Test
  void aaplQuoteRoutesToYahoo() {
    Quote expected = new Quote("AAPL", BigDecimal.valueOf(178), null, Instant.now());
    when(yahoo.getQuote("AAPL")).thenReturn(expected);

    Quote result = router.getQuote("AAPL");

    assertThat(result).isEqualTo(expected);
    verify(yahoo).getQuote("AAPL");
    verifyNoInteractions(coinGecko);
  }

  @Test
  void indicesAndEtfsRouteToYahoo() {
    when(yahoo.getQuote("^GSPC")).thenReturn(new Quote("^GSPC", BigDecimal.ONE, null, Instant.now()));
    when(yahoo.getQuote("SPY")).thenReturn(new Quote("SPY", BigDecimal.ONE, null, Instant.now()));

    router.getQuote("^GSPC");
    router.getQuote("SPY");

    verify(yahoo).getQuote("^GSPC");
    verify(yahoo).getQuote("SPY");
    verifyNoInteractions(coinGecko);
  }

  @Test
  void btcNewsPassesOriginalSymbolToCoinGecko() {
    when(coinGecko.getNews("BTC")).thenReturn(List.of());

    router.getNews("BTC");

    // Original symbol passed — CoinGeckoMarketDataProvider converts to "BTC-USD" for RSS internally
    verify(coinGecko).getNews("BTC");
    verifyNoInteractions(yahoo);
  }

  @Test
  void allFourCryptoSymbolsRouteToCoinGecko() {
    for (String ticker : List.of("BTC", "ETH", "SOL", "XRP")) {
      when(coinGecko.getQuote(org.mockito.ArgumentMatchers.anyString()))
          .thenReturn(new Quote(ticker, BigDecimal.ONE, null, Instant.now()));
      router.getQuote(ticker);
    }
    verify(coinGecko).getQuote("bitcoin");
    verify(coinGecko).getQuote("ethereum");
    verify(coinGecko).getQuote("solana");
    verify(coinGecko).getQuote("ripple");
    verifyNoInteractions(yahoo);
  }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew test --tests "*.RoutingMarketDataProviderTest"
```
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement RoutingMarketDataProvider**

Create `RoutingMarketDataProvider.java`:

```java
package com.austinharlan.trading_dashboard.marketdata;

import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Primary
@Component
@Profile("!dev")
public class RoutingMarketDataProvider implements MarketDataProvider {

  private final YahooFinanceMarketDataProvider yahoo;
  private final CoinGeckoMarketDataProvider coinGecko;
  private final CryptoSymbolMapper mapper;

  // Inject concrete types (not MarketDataProvider) to avoid circular @Primary dependency
  public RoutingMarketDataProvider(
      YahooFinanceMarketDataProvider yahoo,
      CoinGeckoMarketDataProvider coinGecko,
      CryptoSymbolMapper mapper) {
    this.yahoo = yahoo;
    this.coinGecko = coinGecko;
    this.mapper = mapper;
  }

  @Override
  public Quote getQuote(String symbol) {
    if (mapper.isCrypto(symbol)) {
      return coinGecko.getQuote(mapper.toCoinGeckoId(symbol));
    }
    return yahoo.getQuote(symbol);
  }

  @Override
  public CompanyOverview getOverview(String symbol) {
    if (mapper.isCrypto(symbol)) {
      return coinGecko.getOverview(mapper.toCoinGeckoId(symbol));
    }
    return yahoo.getOverview(symbol);
  }

  @Override
  public List<DailyBar> getDailyHistory(String symbol) {
    if (mapper.isCrypto(symbol)) {
      return coinGecko.getDailyHistory(mapper.toCoinGeckoId(symbol));
    }
    return yahoo.getDailyHistory(symbol);
  }

  @Override
  public List<NewsArticle> getNews(String symbol) {
    if (mapper.isCrypto(symbol)) {
      // Pass original ticker (e.g., "BTC") — CoinGeckoMarketDataProvider converts to "BTC-USD"
      return coinGecko.getNews(symbol);
    }
    return yahoo.getNews(symbol);
  }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew test --tests "*.RoutingMarketDataProviderTest"
```
Expected: 5 tests, all PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/austinharlan/trading_dashboard/marketdata/RoutingMarketDataProvider.java \
  src/test/java/com/austinharlan/trading_dashboard/marketdata/RoutingMarketDataProviderTest.java
git commit -m "feat: add RoutingMarketDataProvider as @Primary bean replacing Finnhub"
```

---

### Task 7: Delete RealMarketDataProvider and Full Build Verification

**Files:**
- Delete: `src/main/java/com/austinharlan/trading_dashboard/marketdata/RealMarketDataProvider.java`
- Delete: `src/test/java/com/austinharlan/trading_dashboard/marketdata/RealMarketDataProviderTest.java`

- [ ] **Step 1: Delete the two Finnhub files**

```bash
rm src/main/java/com/austinharlan/trading_dashboard/marketdata/RealMarketDataProvider.java
rm src/test/java/com/austinharlan/trading_dashboard/marketdata/RealMarketDataProviderTest.java
```

- [ ] **Step 2: Run full build (spotless + compile + all tests)**

```bash
./gradlew spotlessApply
./gradlew build
```
Expected: BUILD SUCCESSFUL. All existing tests still pass (integration tests may be skipped locally without Docker — that is expected).

- [ ] **Step 3: Verify no Finnhub references remain**

```bash
grep -r "Finnhub\|finnhub\|FINNHUB\|apiKey\|api-key\|MARKETDATA_API_KEY" \
  src/main/java src/test/java src/main/resources ENV.example
```
Expected: zero matches (log messages inside the now-deleted file are gone; `MARKETDATA_API_KEY` removed from ENV.example)

- [ ] **Step 4: Commit**

```bash
git add -u  # stages deletions
git commit -m "feat: complete Finnhub → Yahoo Finance + CoinGecko migration"
```

- [ ] **Step 5: Tag for easy production rollback reference**

```bash
git tag pre-finnhub-removal HEAD~1
```

---

## Production Migration Steps

After deploying the new JAR:

1. Verify `MARKETDATA_API_KEY` is no longer required — startup succeeds without it (`ProdSecretsValidator` no longer checks it)
2. Optionally remove `MARKETDATA_API_KEY` from `/etc/systemd/system/benji.service` and run `sudo systemctl daemon-reload` (leaving it is harmless)
3. Spot-check: `GET /api/quotes/AAPL`, `GET /api/quotes/BTC`, `/actuator/health`

No database migrations. No frontend changes. No OpenAPI spec changes.
