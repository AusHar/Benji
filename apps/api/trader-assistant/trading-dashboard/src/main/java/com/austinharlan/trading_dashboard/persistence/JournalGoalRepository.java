package com.austinharlan.trading_dashboard.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalGoalRepository extends JpaRepository<JournalGoalEntity, Long> {

  List<JournalGoalEntity> findAllByUserId(Long userId);

  void deleteAllByUserId(Long userId);
}
