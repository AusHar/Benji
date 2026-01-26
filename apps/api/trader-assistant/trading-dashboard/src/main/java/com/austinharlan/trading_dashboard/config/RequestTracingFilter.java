package com.austinharlan.trading_dashboard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that adds request tracing context (correlation ID) to all requests. The correlation ID is
 * extracted from X-Request-ID header or generated if not present. It is added to MDC for logging
 * and returned in the response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestTracingFilter.class);

  public static final String REQUEST_ID_HEADER = "X-Request-ID";
  public static final String MDC_REQUEST_ID = "requestId";
  public static final String MDC_METHOD = "method";
  public static final String MDC_PATH = "path";
  public static final String MDC_REMOTE_ADDR = "remoteAddr";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long startTime = System.currentTimeMillis();

    String requestId = request.getHeader(REQUEST_ID_HEADER);
    if (!StringUtils.hasText(requestId)) {
      requestId = UUID.randomUUID().toString();
    }

    try {
      MDC.put(MDC_REQUEST_ID, requestId);
      MDC.put(MDC_METHOD, request.getMethod());
      MDC.put(MDC_PATH, request.getRequestURI());
      MDC.put(MDC_REMOTE_ADDR, getClientIp(request));

      response.setHeader(REQUEST_ID_HEADER, requestId);

      log.debug(
          "Request started: {} {} from {}",
          request.getMethod(),
          request.getRequestURI(),
          getClientIp(request));

      filterChain.doFilter(request, response);

    } finally {
      long duration = System.currentTimeMillis() - startTime;
      log.info(
          "Request completed: {} {} - {} in {}ms",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          duration);

      MDC.clear();
    }
  }

  private String getClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(xForwardedFor)) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
