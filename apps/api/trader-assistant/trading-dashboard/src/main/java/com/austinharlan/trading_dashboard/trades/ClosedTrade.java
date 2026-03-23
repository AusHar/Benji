package com.austinharlan.trading_dashboard.trades;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClosedTrade(
    String ticker,
    BigDecimal quantity,
    BigDecimal buyPrice,
    BigDecimal sellPrice,
    LocalDate buyDate,
    LocalDate sellDate,
    BigDecimal pnl,
    BigDecimal pnlPercent,
    long holdDays) {}
