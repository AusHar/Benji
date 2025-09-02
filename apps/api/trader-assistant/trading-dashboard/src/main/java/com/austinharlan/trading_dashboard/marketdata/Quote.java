package com.austinharlan.trading_dashboard.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

public record Quote(String symbol, BigDecimal price, Instant timestamp) {}
