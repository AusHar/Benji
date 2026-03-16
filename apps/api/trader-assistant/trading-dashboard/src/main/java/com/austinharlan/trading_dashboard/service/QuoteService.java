package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.marketdata.CompanyOverview;
import com.austinharlan.trading_dashboard.marketdata.DailyBar;
import com.austinharlan.trading_dashboard.marketdata.NewsArticle;
import com.austinharlan.trading_dashboard.marketdata.Quote;
import java.util.List;

public interface QuoteService {
  Quote getCached(String symbol);

  CompanyOverview getCachedOverview(String symbol);

  List<DailyBar> getCachedHistory(String symbol);

  List<NewsArticle> getCachedNews(String symbol);
}
