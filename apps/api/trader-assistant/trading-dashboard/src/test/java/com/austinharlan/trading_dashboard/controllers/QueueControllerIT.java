package com.austinharlan.trading_dashboard.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.austinharlan.trading_dashboard.marketdata.Quote;
import com.austinharlan.trading_dashboard.service.QuoteService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = QuoteController.class)
class QuoteControllerIT {

  @Autowired MockMvc mvc;

  @MockBean private QuoteService quoteService;

  @Test
  void getsQuote() throws Exception {
    when(quoteService.getCached("UUUU"))
        .thenReturn(new Quote("UUUU", BigDecimal.valueOf(100.00), Instant.now()));

    mvc.perform(get("/api/quotes/UUUU"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.symbol").value("UUUU"));
  }
}
