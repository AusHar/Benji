package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.portfolio.PortfolioHolding;
import com.austinharlan.trading_dashboard.portfolio.PortfolioSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PortfolioService {
  List<PortfolioHolding> listHoldings();

  Optional<PortfolioSnapshot> summarize();

  PortfolioHolding addHolding(String ticker, BigDecimal quantity, BigDecimal pricePerShare);

  void deleteHolding(String ticker);

  /**
   * Adjusts the portfolio position for an equity trade. BUY increases qty and basis; SELL reduces
   * them proportionally. No-ops for non-EQUITY asset types and for EXPIRE/EXERCISE sides. Safe to
   * call within an existing transaction — joins via REQUIRED propagation.
   */
  void applyTrade(
      String ticker, String side, String assetType, BigDecimal quantity, BigDecimal pricePerShare);
}
