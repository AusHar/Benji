package com.austinharlan.trading_dashboard.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.austinharlan.trading_dashboard.portfolio.PortfolioHolding;
import com.austinharlan.trading_dashboard.portfolio.PortfolioSnapshot;
import com.austinharlan.trading_dashboard.service.PortfolioService;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PortfolioController.class)
@AutoConfigureMockMvc(addFilters = false)
class PortfolioControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private PortfolioService portfolioService;

  @Test
  void listPortfolioPositionsReturnsPositions() throws Exception {
    List<PortfolioHolding> holdings =
        List.of(
            new PortfolioHolding("AAPL", new BigDecimal("10.5"), new BigDecimal("1500.00")),
            new PortfolioHolding("GOOG", new BigDecimal("5.0"), new BigDecimal("2500.00")));
    when(portfolioService.listHoldings()).thenReturn(holdings);

    mockMvc
        .perform(get("/api/portfolio/positions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.positions").isArray())
        .andExpect(jsonPath("$.positions.length()").value(2))
        .andExpect(jsonPath("$.positions[0].ticker").value("AAPL"))
        .andExpect(jsonPath("$.positions[0].quantity").value(10.5))
        .andExpect(jsonPath("$.positions[0].cost_basis").value(1500.00))
        .andExpect(jsonPath("$.positions[1].ticker").value("GOOG"))
        .andExpect(jsonPath("$.as_of").exists());
  }

  @Test
  void listPortfolioPositionsReturnsNoContentWhenEmpty() throws Exception {
    when(portfolioService.listHoldings()).thenReturn(Collections.emptyList());

    mockMvc.perform(get("/api/portfolio/positions")).andExpect(status().isNoContent());
  }

  @Test
  void getPortfolioSummaryReturnsSummary() throws Exception {
    PortfolioSnapshot snapshot =
        new PortfolioSnapshot(3, new BigDecimal("25.5"), new BigDecimal("5000.00"));
    when(portfolioService.summarize()).thenReturn(Optional.of(snapshot));

    mockMvc
        .perform(get("/api/portfolio/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.positions_count").value(3))
        .andExpect(jsonPath("$.total_quantity").value(25.5))
        .andExpect(jsonPath("$.total_cost_basis").value(5000.00))
        .andExpect(jsonPath("$.as_of").exists());
  }

  @Test
  void getPortfolioSummaryReturnsNoContentWhenEmpty() throws Exception {
    when(portfolioService.summarize()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/portfolio/summary")).andExpect(status().isNoContent());
  }
}
