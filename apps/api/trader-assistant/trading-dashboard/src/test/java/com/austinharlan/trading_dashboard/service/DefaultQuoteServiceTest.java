package com.austinharlan.trading_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.austinharlan.trader.config.CacheConfig;
import com.austinharlan.trading_dashboard.marketdata.MarketDataProvider;
import com.austinharlan.trading_dashboard.marketdata.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(
    classes = {
      CacheConfig.class,
      DefaultQuoteService.class,
      DefaultQuoteServiceTest.TestConfig.class
    })
class DefaultQuoteServiceTest {

  @Autowired private QuoteService quoteService;

  @Autowired private MarketDataProvider provider;

  @Test
  void getCachedInvokesProviderOnlyOnceForRepeatedSymbol() {
    Quote quote = new Quote("AAPL", BigDecimal.ONE, Instant.parse("2024-01-01T00:00:00Z"));
    when(provider.getQuote("AAPL")).thenReturn(quote);

    Quote first = quoteService.getCached("AAPL");
    Quote second = quoteService.getCached("AAPL");

    assertThat(first).isEqualTo(quote);
    assertThat(second).isEqualTo(quote);
    verify(provider, times(1)).getQuote("AAPL");
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    MarketDataProvider marketDataProvider() {
      return Mockito.mock(MarketDataProvider.class);
    }
  }
}
