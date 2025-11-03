package com.austinharlan.trading_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.austinharlan.trader.config.CacheConfig;
import com.austinharlan.trader.config.CacheProperties;
import com.austinharlan.trading_dashboard.marketdata.MarketDataProvider;
import com.austinharlan.trading_dashboard.marketdata.MarketDataRateLimitException;
import com.austinharlan.trading_dashboard.marketdata.Quote;
import java.math.BigDecimal;
import java.time.Duration;
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

  @Autowired private CacheProperties cacheProperties;

  @Test
  void getCachedInvokesProviderOnlyOnceForRepeatedSymbol() {
    cacheProperties.getQuotes().setTtl(Duration.ofMinutes(5));
    Quote quote = new Quote("AAPL", BigDecimal.ONE, Instant.parse("2024-01-01T00:00:00Z"));
    when(provider.getQuote("AAPL")).thenReturn(quote);

    Quote first = quoteService.getCached("AAPL");
    Quote second = quoteService.getCached("AAPL");

    assertThat(first).isEqualTo(quote);
    assertThat(second).isEqualTo(quote);
    verify(provider, times(1)).getQuote("AAPL");
  }

  @Test
  void returnsCachedQuoteWhenRateLimitedDuringRefresh() {
    cacheProperties.getQuotes().setTtl(Duration.ZERO);
    Quote quote = new Quote("MSFT", BigDecimal.TEN, Instant.parse("2024-02-01T00:00:00Z"));
    when(provider.getQuote("MSFT"))
        .thenReturn(quote)
        .thenThrow(new MarketDataRateLimitException("Rate limited"));

    Quote first = quoteService.getCached("MSFT");
    assertThat(first).isEqualTo(quote);

    Quote second = quoteService.getCached("MSFT");
    assertThat(second).isEqualTo(quote);

    verify(provider, times(2)).getQuote("MSFT");
    cacheProperties.getQuotes().setTtl(Duration.ofMinutes(5));
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    MarketDataProvider marketDataProvider() {
      return Mockito.mock(MarketDataProvider.class);
    }

    @Bean
    CacheProperties cacheProperties() {
      CacheProperties properties = new CacheProperties();
      properties.getQuotes().setTtl(Duration.ofMinutes(5));
      properties.getQuotes().setMaximumSize(100);
      return properties;
    }
  }
}
