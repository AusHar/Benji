package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoutingMarketDataProviderTest {

  private YahooFinanceMarketDataProvider yahoo;
  private CoinGeckoMarketDataProvider coinGecko;
  private RoutingMarketDataProvider router;

  @BeforeEach
  void setUp() {
    yahoo = mock(YahooFinanceMarketDataProvider.class);
    coinGecko = mock(CoinGeckoMarketDataProvider.class);

    MarketDataProperties props = new MarketDataProperties();
    props.setBaseUrl("https://query1.finance.yahoo.com");
    props.setQuery2BaseUrl("https://query2.finance.yahoo.com");
    props.setYahooRssBaseUrl("https://finance.yahoo.com");
    props.setCoinGeckoBaseUrl("https://api.coingecko.com");
    props.setCryptoSymbols(
        Map.of("BTC", "bitcoin", "ETH", "ethereum", "SOL", "solana", "XRP", "ripple"));

    CryptoSymbolMapper mapper = new CryptoSymbolMapper(props);
    router = new RoutingMarketDataProvider(yahoo, coinGecko, mapper);
  }

  @Test
  void btcQuoteRoutes_toCoinGeckoWithCoinGeckoId() {
    Quote expected = new Quote("bitcoin", BigDecimal.valueOf(42000), null, Instant.now());
    when(coinGecko.getQuote("bitcoin")).thenReturn(expected);

    Quote result = router.getQuote("BTC");

    assertThat(result).isEqualTo(expected);
    verify(coinGecko).getQuote("bitcoin");
    verifyNoInteractions(yahoo);
  }

  @Test
  void aaplQuoteRoutesToYahoo() {
    Quote expected = new Quote("AAPL", BigDecimal.valueOf(178), null, Instant.now());
    when(yahoo.getQuote("AAPL")).thenReturn(expected);

    Quote result = router.getQuote("AAPL");

    assertThat(result).isEqualTo(expected);
    verify(yahoo).getQuote("AAPL");
    verifyNoInteractions(coinGecko);
  }

  @Test
  void indicesAndEtfsRouteToYahoo() {
    when(yahoo.getQuote("^GSPC"))
        .thenReturn(new Quote("^GSPC", BigDecimal.ONE, null, Instant.now()));
    when(yahoo.getQuote("SPY")).thenReturn(new Quote("SPY", BigDecimal.ONE, null, Instant.now()));

    router.getQuote("^GSPC");
    router.getQuote("SPY");

    verify(yahoo).getQuote("^GSPC");
    verify(yahoo).getQuote("SPY");
    verifyNoInteractions(coinGecko);
  }

  @Test
  void btcNewsPassesOriginalSymbolToCoinGecko() {
    when(coinGecko.getNews("BTC")).thenReturn(List.of());

    router.getNews("BTC");

    // Original symbol passed — CoinGeckoMarketDataProvider converts to "BTC-USD" for RSS internally
    verify(coinGecko).getNews("BTC");
    verifyNoInteractions(yahoo);
  }

  @Test
  void allFourCryptoSymbolsRouteToCoinGecko() {
    for (String ticker : List.of("BTC", "ETH", "SOL", "XRP")) {
      when(coinGecko.getQuote(org.mockito.ArgumentMatchers.anyString()))
          .thenReturn(new Quote(ticker, BigDecimal.ONE, null, Instant.now()));
      router.getQuote(ticker);
    }
    verify(coinGecko).getQuote("bitcoin");
    verify(coinGecko).getQuote("ethereum");
    verify(coinGecko).getQuote("solana");
    verify(coinGecko).getQuote("ripple");
    verifyNoInteractions(yahoo);
  }
}
