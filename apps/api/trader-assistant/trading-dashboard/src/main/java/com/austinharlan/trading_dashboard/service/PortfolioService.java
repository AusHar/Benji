package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.portfolio.PortfolioHolding;
import com.austinharlan.trading_dashboard.portfolio.PortfolioSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PortfolioService {
  List<PortfolioHolding> listHoldings();

  Optional<PortfolioSnapshot> summarize();

  Optional<PortfolioHolding> findByTicker(String ticker);

  PortfolioHolding create(String ticker, BigDecimal quantity, BigDecimal costBasis);

  Optional<PortfolioHolding> update(String ticker, BigDecimal quantity, BigDecimal costBasis);

  boolean delete(String ticker);

  boolean existsByTicker(String ticker);
}
