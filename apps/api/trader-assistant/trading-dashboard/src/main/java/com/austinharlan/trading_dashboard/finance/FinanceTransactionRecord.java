package com.austinharlan.trading_dashboard.finance;

import java.math.BigDecimal;
import java.time.Instant;

public record FinanceTransactionRecord(
    String id,
    Instant postedAt,
    String description,
    BigDecimal amount,
    String category,
    String notes) {}
