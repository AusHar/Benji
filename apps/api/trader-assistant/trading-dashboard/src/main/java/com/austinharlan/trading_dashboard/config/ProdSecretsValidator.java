package com.austinharlan.trading_dashboard.config;

import java.util.Locale;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
class ProdSecretsValidator implements ApplicationRunner {
  private static final Set<String> PLACEHOLDER_VALUES =
      Set.of(
          "changeme",
          "change_me",
          "change-me",
          "replace_me",
          "replace-me",
          "demo",
          "your_real_key",
          "your-api-key",
          "your_api_key",
          "your-strong-db-password",
          "your_strong_db_password",
          "your-strong-actuator-password",
          "your_strong_actuator_password",
          "local-dev-key");

  private final Environment environment;
  private final ApiSecurityProperties apiSecurityProperties;

  ProdSecretsValidator(Environment environment, ApiSecurityProperties apiSecurityProperties) {
    this.environment = environment;
    this.apiSecurityProperties = apiSecurityProperties;
  }

  @Override
  public void run(ApplicationArguments args) {
    requireSecret(
        "TRADING_API_KEY",
        apiSecurityProperties.getKey(),
        "Set trading.api.key (TRADING_API_KEY) before production deployment.");
    requireSecret(
        "SPRING_DATASOURCE_PASSWORD",
        environment.getProperty("spring.datasource.password"),
        "Set SPRING_DATASOURCE_PASSWORD before production deployment.");
    requireSecret(
        "MANAGEMENT_PASSWORD",
        environment.getProperty("spring.security.user.password"),
        "Set MANAGEMENT_PASSWORD before production deployment.");
  }

  private void requireSecret(String name, String value, String guidance) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException("PROD-REQUIRED: " + name + " is not set. " + guidance);
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (PLACEHOLDER_VALUES.contains(normalized)) {
      throw new IllegalStateException(
          "PROD-REQUIRED: "
              + name
              + " is using a placeholder value. "
              + guidance
              + " Replace it before going live.");
    }
  }
}
