package com.austinharlan.trading_dashboard.finance;

import java.math.BigDecimal;
import java.time.Instant;

public record FinanceSummaryData(
    BigDecimal monthToDateSpend,
    BigDecimal averageDailySpend,
    BigDecimal projectedMonthEndSpend,
    Instant asOf) {}
