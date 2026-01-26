package com.austinharlan.trading_dashboard.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
class CorsConfig {

  @Bean
  @ConfigurationProperties(prefix = "trading.cors")
  CorsProperties corsProperties() {
    return new CorsProperties();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
    CorsConfiguration configuration = new CorsConfiguration();

    if (corsProperties.getAllowedOrigins() != null
        && !corsProperties.getAllowedOrigins().isEmpty()) {
      configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
    } else {
      configuration.setAllowedOrigins(List.of());
    }

    configuration.setAllowedMethods(
        corsProperties.getAllowedMethods() != null
            ? corsProperties.getAllowedMethods()
            : List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

    configuration.setAllowedHeaders(
        corsProperties.getAllowedHeaders() != null
            ? corsProperties.getAllowedHeaders()
            : List.of("Authorization", "Content-Type", "X-API-KEY"));

    configuration.setExposedHeaders(
        corsProperties.getExposedHeaders() != null
            ? corsProperties.getExposedHeaders()
            : List.of());

    configuration.setAllowCredentials(corsProperties.isAllowCredentials());

    if (corsProperties.getMaxAge() != null) {
      configuration.setMaxAge(corsProperties.getMaxAge());
    }

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  static class CorsProperties {
    private List<String> allowedOrigins;
    private List<String> allowedMethods;
    private List<String> allowedHeaders;
    private List<String> exposedHeaders;
    private boolean allowCredentials = false;
    private Long maxAge = 3600L;

    public List<String> getAllowedOrigins() {
      return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
      this.allowedOrigins = allowedOrigins;
    }

    public List<String> getAllowedMethods() {
      return allowedMethods;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
      this.allowedMethods = allowedMethods;
    }

    public List<String> getAllowedHeaders() {
      return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
      this.allowedHeaders = allowedHeaders;
    }

    public List<String> getExposedHeaders() {
      return exposedHeaders;
    }

    public void setExposedHeaders(List<String> exposedHeaders) {
      this.exposedHeaders = exposedHeaders;
    }

    public boolean isAllowCredentials() {
      return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
      this.allowCredentials = allowCredentials;
    }

    public Long getMaxAge() {
      return maxAge;
    }

    public void setMaxAge(Long maxAge) {
      this.maxAge = maxAge;
    }
  }
}
