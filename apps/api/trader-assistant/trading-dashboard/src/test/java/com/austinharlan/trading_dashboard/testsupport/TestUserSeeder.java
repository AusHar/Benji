package com.austinharlan.trading_dashboard.testsupport;

import com.austinharlan.trading_dashboard.persistence.UserEntity;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestUserSeeder implements ApplicationRunner {

  private final UserRepository userRepository;

  public TestUserSeeder(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (userRepository.findByApiKey("test-api-key").isEmpty()) {
      userRepository.save(new UserEntity("test-api-key", "Test User", true, false));
    }
  }
}
