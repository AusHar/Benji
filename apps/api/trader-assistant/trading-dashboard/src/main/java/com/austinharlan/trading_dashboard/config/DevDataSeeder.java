package com.austinharlan.trading_dashboard.config;

import com.austinharlan.trading_dashboard.persistence.UserEntity;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
class DevDataSeeder {

  private final UserRepository userRepository;

  DevDataSeeder(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @PostConstruct
  void seed() {
    if (userRepository.findByApiKey("dev").isEmpty()) {
      userRepository.save(new UserEntity("dev", "Dev User", true, false));
    }
    if (userRepository.findByApiKey("demo").isEmpty()) {
      userRepository.save(new UserEntity("demo", "Demo User", false, true));
    }
  }
}
