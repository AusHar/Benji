package com.austinharlan.trader.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
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
    CaffeineCacheManager cacheManager = new CaffeineCacheManager("quotes");
    cacheManager.setCaffeine(
        Caffeine.newBuilder()
            .expireAfterWrite(cacheProperties.getQuotes().getTtl())
            .maximumSize(cacheProperties.getQuotes().getMaximumSize()));
    return cacheManager;
  }
}
