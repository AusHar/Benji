package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trader.config.CacheProperties;
import com.austinharlan.trading_dashboard.marketdata.MarketDataProvider;
import com.austinharlan.trading_dashboard.marketdata.MarketDataRateLimitException;
import com.austinharlan.trading_dashboard.marketdata.Quote;
import java.time.Duration;
import java.time.Instant;
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
  private final Map<String, Instant> cacheTimestamps = new ConcurrentHashMap<>();

  public DefaultQuoteService(
      MarketDataProvider provider, CacheManager cacheManager, CacheProperties cacheProperties) {
    this.provider = provider;
    this.cacheProperties = cacheProperties;
    this.quotesCache = cacheManager != null ? cacheManager.getCache("quotes") : null;
  }

  @Override
  public Quote getCached(String symbol) {
    Quote cached = getCachedQuote(symbol);
    Duration ttl = cacheProperties.getQuotes().getTtl();
    if (cached != null && !isStale(symbol, ttl)) {
      return cached;
    }

    try {
      Quote fresh = provider.getQuote(symbol);
      cacheQuote(symbol, fresh);
      return fresh;
    } catch (MarketDataRateLimitException ex) {
      Quote fallback = getCachedQuote(symbol);
      if (fallback != null) {
        return fallback;
      }
      throw ex;
    }
  }

  private Quote getCachedQuote(String symbol) {
    if (quotesCache == null) {
      return null;
    }
    return quotesCache.get(symbol, Quote.class);
  }

  private void cacheQuote(String symbol, Quote quote) {
    if (quotesCache != null && quote != null) {
      quotesCache.put(symbol, quote);
      cacheTimestamps.put(symbol, Instant.now());
    }
  }

  private boolean isStale(String symbol, Duration ttl) {
    if (ttl.isZero() || ttl.isNegative()) {
      return true;
    }
    Instant cachedAt = cacheTimestamps.get(symbol);
    if (cachedAt == null) {
      return true;
    }
    return cachedAt.plus(ttl).isBefore(Instant.now());
  }
}
