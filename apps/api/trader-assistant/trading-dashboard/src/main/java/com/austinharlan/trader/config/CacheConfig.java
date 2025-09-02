package com.austinharlan.trader.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {
  @Bean
  CacheManager cacheManager() {
    return new CaffeineCacheManager();
  }
}
