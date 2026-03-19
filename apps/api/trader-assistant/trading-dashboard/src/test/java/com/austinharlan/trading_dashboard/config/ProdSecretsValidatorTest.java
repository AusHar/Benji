package com.austinharlan.trading_dashboard.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class ProdSecretsValidatorTest {

  @Test
  void runRejectsPlaceholderTradingApiKey() {
    ProdSecretsValidator validator =
        new ProdSecretsValidator(baseEnvironment(), apiSecurityProperties("replace_me"));

    assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PROD-REQUIRED: TRADING_API_KEY")
        .hasMessageContaining("placeholder value");
  }

  @Test
  void runRejectsMissingDatasourcePassword() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("spring.security.user.password", "strong-management-password");

    ProdSecretsValidator validator =
        new ProdSecretsValidator(environment, apiSecurityProperties("real-api-key"));

    assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PROD-REQUIRED: SPRING_DATASOURCE_PASSWORD")
        .hasMessageContaining("not set");
  }

  @Test
  void runAllowsRealSecrets() {
    ProdSecretsValidator validator =
        new ProdSecretsValidator(baseEnvironment(), apiSecurityProperties("real-api-key"));

    assertThatCode(() -> validator.run(new DefaultApplicationArguments(new String[0])))
        .doesNotThrowAnyException();
  }

  private static MockEnvironment baseEnvironment() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("spring.datasource.password", "strong-db-password");
    environment.setProperty("spring.security.user.password", "strong-management-password");
    return environment;
  }

  private static ApiSecurityProperties apiSecurityProperties(String key) {
    ApiSecurityProperties properties = new ApiSecurityProperties();
    properties.setKey(key);
    return properties;
  }
}
