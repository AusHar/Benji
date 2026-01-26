package com.austinharlan.trading_dashboard.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPositionEntity, Long> {
  Optional<PortfolioPositionEntity> findByTicker(String ticker);

  Optional<PortfolioPositionEntity> findByTickerIgnoreCase(String ticker);

  boolean existsByTickerIgnoreCase(String ticker);

  void deleteByTickerIgnoreCase(String ticker);
}
