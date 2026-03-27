package com.austinharlan.trading_dashboard.config;

import com.austinharlan.trading_dashboard.persistence.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
class ApiKeyInitializer {

  private final UserRepository userRepository;
  private final ApiSecurityProperties apiSecurityProperties;

  ApiKeyInitializer(UserRepository userRepository, ApiSecurityProperties apiSecurityProperties) {
    this.userRepository = userRepository;
    this.apiSecurityProperties = apiSecurityProperties;
  }

  @PostConstruct
  void syncOwnerApiKey() {
    String envKey = apiSecurityProperties.getKey();
    // Look up by role, not by placeholder key — the key changes after first startup
    var ownerUser =
        userRepository.findByAdminTrueAndDemoFalse()
            .orElseThrow(() -> new IllegalStateException(
                "PROD-REQUIRED: Owner user not found in users table. "
                    + "Ensure V5 migration has run successfully."));
    if (!envKey.equals(ownerUser.getApiKey())) {
      ownerUser.setApiKey(envKey);
      userRepository.save(ownerUser);
    }
  }
}
