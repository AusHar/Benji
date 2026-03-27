package com.austinharlan.trading_dashboard.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPositionEntity, Long> {

  List<PortfolioPositionEntity> findAllByUserId(Long userId);

  Optional<PortfolioPositionEntity> findByUserIdAndTicker(Long userId, String ticker);

  void deleteByUserIdAndTicker(Long userId, String ticker);

  void deleteAllByUserId(Long userId);
}
