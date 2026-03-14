package com.austinharlan.trading_dashboard.marketdata;

import java.math.BigDecimal;

public record CompanyOverview(
    String symbol,
    String name,
    String sector,
    String industry,
    BigDecimal marketCap,
    BigDecimal pe,
    BigDecimal eps,
    BigDecimal dividendYield,
    BigDecimal beta,
    BigDecimal fiftyTwoWeekHigh,
    BigDecimal fiftyTwoWeekLow) {}
