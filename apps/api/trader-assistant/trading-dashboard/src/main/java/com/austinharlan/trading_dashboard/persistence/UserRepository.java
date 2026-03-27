package com.austinharlan.trading_dashboard.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByApiKey(String apiKey);

  Optional<UserEntity> findByIsDemoTrue();

  Optional<UserEntity> findByAdminTrueAndDemoFalse();
}
