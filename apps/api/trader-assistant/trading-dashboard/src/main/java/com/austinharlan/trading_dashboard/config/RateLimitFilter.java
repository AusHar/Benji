package com.austinharlan.trading_dashboard.config;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that enforces rate limiting on API requests. Uses a token bucket algorithm with separate
 * limits for quote endpoints (to protect AlphaVantage quota) and general endpoints.
 */
@Component
@Profile("!dev")
@Order(10)
public class RateLimitFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

  private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
  private static final String RATE_LIMIT_RETRY_AFTER_HEADER = "Retry-After";

  private final RateLimitConfig.RateLimitService rateLimitService;
  private final ApiSecurityProperties apiSecurityProperties;

  public RateLimitFilter(
      RateLimitConfig.RateLimitService rateLimitService,
      ApiSecurityProperties apiSecurityProperties) {
    this.rateLimitService = rateLimitService;
    this.apiSecurityProperties = apiSecurityProperties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !rateLimitService.isEnabled()
        || path.startsWith("/actuator/")
        || !path.startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String clientKey = resolveClientKey(request);
    boolean isQuoteEndpoint = request.getRequestURI().startsWith("/api/quotes");

    Bucket bucket = rateLimitService.resolveBucket(clientKey, isQuoteEndpoint);
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

    if (probe.isConsumed()) {
      response.setHeader(RATE_LIMIT_REMAINING_HEADER, String.valueOf(probe.getRemainingTokens()));
      filterChain.doFilter(request, response);
    } else {
      long waitSeconds = rateLimitService.getWaitTimeSeconds(bucket);

      log.warn(
          "Rate limit exceeded for client {} on {} {}",
          clientKey,
          request.getMethod(),
          request.getRequestURI());

      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setHeader(RATE_LIMIT_REMAINING_HEADER, "0");
      response.setHeader(RATE_LIMIT_RETRY_AFTER_HEADER, String.valueOf(waitSeconds));
      response
          .getOutputStream()
          .write(
              "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please try again later.\"}"
                  .getBytes(StandardCharsets.UTF_8));
    }
  }

  private String resolveClientKey(HttpServletRequest request) {
    if (apiSecurityProperties.isEnabled()) {
      String apiKey = request.getHeader(apiSecurityProperties.getHeaderName());
      if (StringUtils.hasText(apiKey)) {
        return "apikey:" + apiKey.substring(0, Math.min(8, apiKey.length()));
      }
    }

    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(xForwardedFor)) {
      return "ip:" + xForwardedFor.split(",")[0].trim();
    }

    return "ip:" + request.getRemoteAddr();
  }
}
