package com.austinharlan.trading_dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@Profile("!dev")
class ActuatorSecurityConfig {
  private final ApiSecurityProperties apiSecurityProperties;
  private final ApiKeyAuthFilter apiKeyAuthFilter;

  ActuatorSecurityConfig(
      ApiSecurityProperties apiSecurityProperties, ApiKeyAuthFilter apiKeyAuthFilter) {
    this.apiSecurityProperties = apiSecurityProperties;
    this.apiKeyAuthFilter = apiKeyAuthFilter;
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  SecurityFilterChain actuatorSecurity(HttpSecurity http) throws Exception {
    http.securityMatcher("/actuator/**")
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/health/**")
                    .hasRole("ACTUATOR")
                    .anyRequest()
                    .denyAll())
        .httpBasic(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception {
    if (apiSecurityProperties.isEnabled()) {
      http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
          .addFilterBefore(apiKeyAuthFilter, AnonymousAuthenticationFilter.class);
    } else {
      http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    }

    http.httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }
}
