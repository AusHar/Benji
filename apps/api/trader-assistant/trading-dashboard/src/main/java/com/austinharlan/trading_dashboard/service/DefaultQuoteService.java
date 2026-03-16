package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trader.config.CacheProperties;
import com.austinharlan.trading_dashboard.marketdata.CompanyOverview;
import com.austinharlan.trading_dashboard.marketdata.DailyBar;
import com.austinharlan.trading_dashboard.marketdata.MarketDataProvider;
import com.austinharlan.trading_dashboard.marketdata.MarketDataRateLimitException;
import com.austinharlan.trading_dashboard.marketdata.NewsArticle;
import com.austinharlan.trading_dashboard.marketdata.Quote;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class DefaultQuoteService implements QuoteService {
  private final MarketDataProvider provider;
  private final CacheProperties cacheProperties;
  private final Cache quotesCache;
  private final Cache overviewsCache;
  private final Cache historyCache;
  private final Cache newsCache;
  private final Map<String, Instant> cacheTimestamps = new ConcurrentHashMap<>();

  public DefaultQuoteService(
      MarketDataProvider provider, CacheManager cacheManager, CacheProperties cacheProperties) {
    this.provider = provider;
    this.cacheProperties = cacheProperties;
    this.quotesCache = cacheManager != null ? cacheManager.getCache("quotes") : null;
    this.overviewsCache = cacheManager != null ? cacheManager.getCache("overviews") : null;
    this.historyCache = cacheManager != null ? cacheManager.getCache("history") : null;
    this.newsCache = cacheManager != null ? cacheManager.getCache("news") : null;
  }

  @Override
  public Quote getCached(String symbol) {
    return fetchWithCache(
        symbol,
        "quote",
        quotesCache,
        Quote.class,
        cacheProperties.getQuotes().getTtl(),
        () -> provider.getQuote(symbol));
  }

  @Override
  public CompanyOverview getCachedOverview(String symbol) {
    return fetchWithCache(
        symbol,
        "overview",
        overviewsCache,
        CompanyOverview.class,
        cacheProperties.getOverview().getTtl(),
        () -> provider.getOverview(symbol));
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<DailyBar> getCachedHistory(String symbol) {
    String cacheKey = "history:" + symbol;
    List<DailyBar> cached = getCachedValue(historyCache, cacheKey, List.class);
    Duration ttl = cacheProperties.getHistory().getTtl();
    if (cached != null && !isStale(cacheKey, ttl)) {
      return cached;
    }

    try {
      List<DailyBar> fresh = provider.getDailyHistory(symbol);
      putCache(historyCache, cacheKey, fresh);
      return fresh;
    } catch (MarketDataRateLimitException ex) {
      List<DailyBar> fallback = getCachedValue(historyCache, cacheKey, List.class);
      if (fallback != null) {
        return fallback;
      }
      throw ex;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NewsArticle> getCachedNews(String symbol) {
    String cacheKey = "news:" + symbol;
    List<NewsArticle> cached = getCachedValue(newsCache, cacheKey, List.class);
    Duration ttl = cacheProperties.getNews().getTtl();
    if (cached != null && !isStale(cacheKey, ttl)) {
      return cached;
    }

    try {
      List<NewsArticle> fresh = provider.getNews(symbol);
      putCache(newsCache, cacheKey, fresh);
      return fresh;
    } catch (MarketDataRateLimitException ex) {
      List<NewsArticle> fallback = getCachedValue(newsCache, cacheKey, List.class);
      if (fallback != null) {
        return fallback;
      }
      throw ex;
    }
  }

  private <T> T fetchWithCache(
      String symbol,
      String namespace,
      Cache cache,
      Class<T> type,
      Duration ttl,
      java.util.function.Supplier<T> fetcher) {
    String cacheKey = namespace + ":" + symbol;
    T cached = getCachedValue(cache, cacheKey, type);
    if (cached != null && !isStale(cacheKey, ttl)) {
      return cached;
    }

    try {
      T fresh = fetcher.get();
      putCache(cache, cacheKey, fresh);
      return fresh;
    } catch (MarketDataRateLimitException ex) {
      T fallback = getCachedValue(cache, cacheKey, type);
      if (fallback != null) {
        return fallback;
      }
      throw ex;
    }
  }

  private <T> T getCachedValue(Cache cache, String key, Class<T> type) {
    if (cache == null) {
      return null;
    }
    return cache.get(key, type);
  }

  private void putCache(Cache cache, String key, Object value) {
    if (cache != null && value != null) {
      cache.put(key, value);
      cacheTimestamps.put(key, Instant.now());
    }
  }

  private boolean isStale(String key, Duration ttl) {
    if (ttl.isZero() || ttl.isNegative()) {
      return true;
    }
    Instant cachedAt = cacheTimestamps.get(key);
    if (cachedAt == null) {
      return true;
    }
    return cachedAt.plus(ttl).isBefore(Instant.now());
  }
}
