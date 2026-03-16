package com.austinharlan.trader.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
  private final CacheProperties cacheProperties;

  public CacheConfig(CacheProperties cacheProperties) {
    this.cacheProperties = cacheProperties;
  }

  @Bean
  CacheManager cacheManager() {
    SimpleCacheManager manager = new SimpleCacheManager();
    manager.setCaches(
        List.of(
            buildCache(
                "quotes",
                cacheProperties.getQuotes().getTtl(),
                cacheProperties.getQuotes().getMaximumSize()),
            buildCache(
                "overviews",
                cacheProperties.getOverview().getTtl(),
                cacheProperties.getOverview().getMaximumSize()),
            buildCache(
                "history",
                cacheProperties.getHistory().getTtl(),
                cacheProperties.getHistory().getMaximumSize()),
            buildCache(
                "news",
                cacheProperties.getNews().getTtl(),
                cacheProperties.getNews().getMaximumSize())));
    return manager;
  }

  private CaffeineCache buildCache(String name, Duration ttl, long maxSize) {
    return new CaffeineCache(
        name, Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build());
  }
}
