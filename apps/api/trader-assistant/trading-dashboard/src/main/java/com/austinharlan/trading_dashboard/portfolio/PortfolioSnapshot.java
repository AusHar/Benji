package com.austinharlan.trading_dashboard.portfolio;

import java.math.BigDecimal;

public record PortfolioSnapshot(
    int positionsCount, BigDecimal totalQuantity, BigDecimal totalCostBasis) {}
