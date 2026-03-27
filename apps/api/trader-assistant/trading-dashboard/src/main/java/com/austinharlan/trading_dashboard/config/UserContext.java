package com.austinharlan.trading_dashboard.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public record UserContext(long userId, String displayName, boolean isDemo, boolean isAdmin) {

  public static UserContext current() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof UserContext ctx)) {
      throw new IllegalStateException(
          "No UserContext in SecurityContext — ensure request passed through auth filter");
    }
    return ctx;
  }
}
