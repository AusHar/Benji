package com.austinharlan.trading_dashboard.portfolio;

import java.math.BigDecimal;

public record PortfolioHolding(String ticker, BigDecimal quantity, BigDecimal costBasis) {}
