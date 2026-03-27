package com.austinharlan.trading_dashboard.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByApiKey(String apiKey);

  Optional<UserEntity> findByDemoTrue();

  @Query("SELECT u FROM UserEntity u WHERE u.demo = true")
  Optional<UserEntity> findByIsDemoTrue();

  Optional<UserEntity> findByAdminTrueAndDemoFalse();
}
