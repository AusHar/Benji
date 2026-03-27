package com.austinharlan.trading_dashboard.config;

import com.austinharlan.trading_dashboard.persistence.UserEntity;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
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
  private final UserRepository userRepository;

  ApiKeyAuthFilter(ApiSecurityProperties properties, UserRepository userRepository) {
    this.properties = properties;
    this.userRepository = userRepository;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri.equals("/")
        || uri.equals("/index.html")
        || uri.startsWith("/actuator/")
        || uri.startsWith("/swagger-ui")
        || uri.startsWith("/v3/api-docs")
        || uri.equals("/api/demo/session");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String providedKey = request.getHeader(properties.getHeaderName());
    if (!StringUtils.hasText(providedKey)) {
      sendUnauthorized(response);
      return;
    }

    Optional<UserEntity> user = userRepository.findByApiKey(providedKey);
    if (user.isEmpty()) {
      sendUnauthorized(response);
      return;
    }

    UserEntity u = user.get();
    UserContext ctx = new UserContext(u.getId(), u.getDisplayName(), u.isDemo(), u.isAdmin());
    PreAuthenticatedAuthenticationToken authentication =
        new PreAuthenticatedAuthenticationToken(ctx, "", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(authentication);
    try {
      filterChain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void sendUnauthorized(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response
        .getOutputStream()
        .write("{\"error\":\"invalid_api_key\"}".getBytes(StandardCharsets.UTF_8));
  }
}
