package com.austinharlan.trading_dashboard.config;

import com.austinharlan.trading_dashboard.persistence.UserEntity;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("dev")
class DevUserFilter extends OncePerRequestFilter {

  private final UserRepository userRepository;
  private volatile UserContext cachedCtx;

  DevUserFilter(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (cachedCtx == null) {
      UserEntity devUser =
          userRepository
              .findByApiKey("dev")
              .orElseThrow(() -> new IllegalStateException("Dev user not found"));
      cachedCtx =
          new UserContext(
              devUser.getId(), devUser.getDisplayName(), devUser.isDemo(), devUser.isAdmin());
    }
    PreAuthenticatedAuthenticationToken auth =
        new PreAuthenticatedAuthenticationToken(cachedCtx, "", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);
    try {
      filterChain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
