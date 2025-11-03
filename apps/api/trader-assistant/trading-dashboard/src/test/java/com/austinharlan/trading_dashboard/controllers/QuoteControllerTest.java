package com.austinharlan.trading_dashboard.controllers;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.austinharlan.trading_dashboard.marketdata.MarketDataRateLimitException;
import com.austinharlan.trading_dashboard.marketdata.Quote;
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

  @Autowired private MockMvc mvc;

  @MockBean private QuoteService quoteService;

  @Test
  void getQuote_returnsJson() throws Exception {
    Instant timestamp = Instant.parse("2024-01-01T00:00:00Z");
    when(quoteService.getCached("UUUU"))
        .thenReturn(new Quote("UUUU", BigDecimal.valueOf(100.00), timestamp));

    mvc.perform(get("/api/quotes/UUUU"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.symbol", is("UUUU")))
        .andExpect(jsonPath("$.timestamp", is(timestamp.toString())));
  }

  @Test
  void getQuoteReturns429WhenRateLimited() throws Exception {
    when(quoteService.getCached("TSLA"))
        .thenThrow(new MarketDataRateLimitException("AlphaVantage rate limit reached"));

    mvc.perform(get("/api/quotes/TSLA"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error", is("rate_limited")));
  }
}
