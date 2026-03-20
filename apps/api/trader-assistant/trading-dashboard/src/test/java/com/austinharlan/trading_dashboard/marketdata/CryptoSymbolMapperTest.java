package com.austinharlan.trading_dashboard.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CryptoSymbolMapperTest {

  private CryptoSymbolMapper mapper;

  @BeforeEach
  void setUp() {
    MarketDataProperties properties = new MarketDataProperties();
    // Only cryptoSymbols matters to CryptoSymbolMapper; URL fields use placeholder values to
    // satisfy @NotBlank validation on MarketDataProperties
    properties.setBaseUrl("http://test");
    properties.setQuery2BaseUrl("http://test");
    properties.setYahooRssBaseUrl("http://test");
    properties.setCoinGeckoBaseUrl("http://test");
    properties.setCryptoSymbols(
        Map.of("BTC", "bitcoin", "ETH", "ethereum", "SOL", "solana", "XRP", "ripple"));
    mapper = new CryptoSymbolMapper(properties);
  }

  @Test
  void isCryptoReturnsTrueForKnownSymbols() {
    assertThat(mapper.isCrypto("BTC")).isTrue();
    assertThat(mapper.isCrypto("ETH")).isTrue();
    assertThat(mapper.isCrypto("SOL")).isTrue();
    assertThat(mapper.isCrypto("XRP")).isTrue();
  }

  @Test
  void isCryptoReturnsFalseForStocksAndNull() {
    assertThat(mapper.isCrypto("AAPL")).isFalse();
    assertThat(mapper.isCrypto("SPY")).isFalse();
    assertThat(mapper.isCrypto("^GSPC")).isFalse();
    assertThat(mapper.isCrypto(null)).isFalse();
  }

  @Test
  void isCryptoIsCaseInsensitive() {
    assertThat(mapper.isCrypto("btc")).isTrue();
    assertThat(mapper.isCrypto("Eth")).isTrue();
  }

  @Test
  void toCoinGeckoIdMapsAllFourCoins() {
    assertThat(mapper.toCoinGeckoId("BTC")).isEqualTo("bitcoin");
    assertThat(mapper.toCoinGeckoId("ETH")).isEqualTo("ethereum");
    assertThat(mapper.toCoinGeckoId("SOL")).isEqualTo("solana");
    assertThat(mapper.toCoinGeckoId("XRP")).isEqualTo("ripple");
  }

  @Test
  void toYahooRssSymbolAppendsDashUsd() {
    assertThat(mapper.toYahooRssSymbol("BTC")).isEqualTo("BTC-USD");
    assertThat(mapper.toYahooRssSymbol("eth")).isEqualTo("ETH-USD");
    assertThat(mapper.toYahooRssSymbol(null)).isNull();
  }
}
