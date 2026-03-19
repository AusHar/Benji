package com.austinharlan.trading_dashboard.marketdata;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CryptoSymbolMapper {

  private final Map<String, String> cryptoSymbols;

  public CryptoSymbolMapper(MarketDataProperties properties) {
    Map<String, String> symbols = properties.getCryptoSymbols();
    this.cryptoSymbols = symbols != null ? symbols : Map.of();
  }

  public boolean isCrypto(String symbol) {
    return symbol != null && cryptoSymbols.containsKey(symbol.toUpperCase(Locale.ROOT));
  }

  public String toCoinGeckoId(String symbol) {
    if (symbol == null) return null;
    return cryptoSymbols.get(symbol.toUpperCase(Locale.ROOT));
  }

  public String toYahooRssSymbol(String symbol) {
    if (symbol == null) return null;
    return symbol.toUpperCase(Locale.ROOT) + "-USD";
  }
}
