package com.austinharlan.trading_dashboard.marketdata;

import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Primary
@Component
@Profile("!dev")
public class RoutingMarketDataProvider implements MarketDataProvider {

  private final YahooFinanceMarketDataProvider yahoo;
  private final CoinGeckoMarketDataProvider coinGecko;
  private final CryptoSymbolMapper mapper;

  // Inject concrete types (not MarketDataProvider) to avoid circular @Primary dependency
  public RoutingMarketDataProvider(
      YahooFinanceMarketDataProvider yahoo,
      CoinGeckoMarketDataProvider coinGecko,
      CryptoSymbolMapper mapper) {
    this.yahoo = yahoo;
    this.coinGecko = coinGecko;
    this.mapper = mapper;
  }

  @Override
  public Quote getQuote(String symbol) {
    if (mapper.isCrypto(symbol)) {
      return coinGecko.getQuote(mapper.toCoinGeckoId(symbol));
    }
    return yahoo.getQuote(symbol);
  }

  @Override
  public CompanyOverview getOverview(String symbol) {
    if (mapper.isCrypto(symbol)) {
      return coinGecko.getOverview(mapper.toCoinGeckoId(symbol));
    }
    return yahoo.getOverview(symbol);
  }

  @Override
  public List<DailyBar> getDailyHistory(String symbol) {
    if (mapper.isCrypto(symbol)) {
      return coinGecko.getDailyHistory(mapper.toCoinGeckoId(symbol));
    }
    return yahoo.getDailyHistory(symbol);
  }

  @Override
  public List<NewsArticle> getNews(String symbol) {
    if (mapper.isCrypto(symbol)) {
      // Pass original ticker (e.g., "BTC") — CoinGeckoMarketDataProvider converts to "BTC-USD"
      return coinGecko.getNews(symbol);
    }
    return yahoo.getNews(symbol);
  }
}
