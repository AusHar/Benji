package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.portfolio.PortfolioHolding;
import com.austinharlan.trading_dashboard.portfolio.PortfolioSnapshot;
import java.util.List;
import java.util.Optional;

public interface PortfolioService {
  List<PortfolioHolding> listHoldings();

  Optional<PortfolioSnapshot> summarize();
}
