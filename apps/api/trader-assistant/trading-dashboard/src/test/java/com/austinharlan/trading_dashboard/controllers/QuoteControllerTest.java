package com.austinharlan.trading_dashboard.controllers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.austinharlan.trading_dashboard.marketdata.MarketDataRateLimitException;
import com.austinharlan.trading_dashboard.marketdata.Quote;
import com.austinharlan.trading_dashboard.marketdata.QuoteNotFoundException;
import com.austinharlan.trading_dashboard.service.QuoteService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = QuoteController.class)
@AutoConfigureMockMvc(addFilters = false)
class QuoteControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private QuoteService quoteService;

  @Test
  void getQuoteReturnsQuoteResponse() throws Exception {
    Instant asOf = Instant.parse("2024-01-01T00:00:00Z");
    when(quoteService.getCached("AAPL"))
        .thenReturn(new Quote("AAPL", BigDecimal.valueOf(123.45), asOf));

    mockMvc
        .perform(get("/api/quotes/aapl"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.symbol").value("AAPL"))
        .andExpect(jsonPath("$.price").value(123.45))
        .andExpect(jsonPath("$.currency").value("USD"));
  }

  @Test
  void getQuoteRejectsInvalidTicker() throws Exception {
    mockMvc
        .perform(get("/api/quotes/%20"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SYMBOL_INVALID"));
  }

  @Test
  void getQuoteReturnsNotFound() throws Exception {
    when(quoteService.getCached("MSFT"))
        .thenThrow(new QuoteNotFoundException("Quote was not found for MSFT"));

    mockMvc
        .perform(get("/api/quotes/msft"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
  }

  @Test
  void getQuotePropagatesProviderFailures() throws Exception {
    when(quoteService.getCached(anyString()))
        .thenThrow(new MarketDataRateLimitException("AlphaVantage rate limit reached"));

    mockMvc
        .perform(get("/api/quotes/goog"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }
}
