package com.austinharlan.trading_dashboard.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class UserContextTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void current_returnsUserContext_whenPrincipalIsSet() {
    var ctx = new UserContext(42L, "Alice", false, true);
    var auth = new PreAuthenticatedAuthenticationToken(ctx, "", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    UserContext result = UserContext.current();

    assertThat(result.userId()).isEqualTo(42L);
    assertThat(result.displayName()).isEqualTo("Alice");
    assertThat(result.isDemo()).isFalse();
    assertThat(result.isAdmin()).isTrue();
  }

  @Test
  void current_throwsIllegalState_whenNoAuthentication() {
    assertThatThrownBy(UserContext::current)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No UserContext");
  }

  @Test
  void current_throwsIllegalState_whenPrincipalIsWrongType() {
    var auth =
        new PreAuthenticatedAuthenticationToken("not-a-user-context", "", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThatThrownBy(UserContext::current)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No UserContext");
  }
}
