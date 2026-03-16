package com.austinharlan.trading_dashboard.marketdata;

import java.time.Instant;

public record NewsArticle(
    long id,
    String headline,
    String summary,
    String source,
    String url,
    String image,
    Instant publishedAt) {}
