package com.austinharlan.trading_dashboard.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPositionEntity, Long> {

  List<PortfolioPositionEntity> findAllByUserId(Long userId);

  Optional<PortfolioPositionEntity> findByUserIdAndTicker(Long userId, String ticker);

  @Transactional
  void deleteByUserIdAndTicker(Long userId, String ticker);

  @Transactional
  void deleteAllByUserId(Long userId);
}
