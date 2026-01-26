package com.austinharlan.trading_dashboard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("!dev")
class ApiKeyAuthFilter extends OncePerRequestFilter {

  private final ApiSecurityProperties properties;

  ApiKeyAuthFilter(ApiSecurityProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator/") || !properties.isEnabled();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String providedKey = request.getHeader(properties.getHeaderName());
    if (!StringUtils.hasText(providedKey) || !isKeyValid(providedKey)) {
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response
          .getOutputStream()
          .write("{\"error\":\"invalid_api_key\"}".getBytes(StandardCharsets.UTF_8));
      return;
    }

    PreAuthenticatedAuthenticationToken authentication =
        new PreAuthenticatedAuthenticationToken("api-key", "", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(authentication);
    try {
      filterChain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  /**
   * Validates the provided API key using constant-time comparison to prevent timing attacks.
   *
   * @param providedKey the API key from the request header
   * @return true if the key matches the configured key
   */
  private boolean isKeyValid(String providedKey) {
    byte[] providedBytes = providedKey.getBytes(StandardCharsets.UTF_8);
    byte[] expectedBytes = properties.getKey().getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(providedBytes, expectedBytes);
  }
}
