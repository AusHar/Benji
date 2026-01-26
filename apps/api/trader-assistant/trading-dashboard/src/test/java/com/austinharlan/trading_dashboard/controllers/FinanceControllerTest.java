package com.austinharlan.trading_dashboard.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.austinharlan.trading_dashboard.finance.FinanceSummaryData;
import com.austinharlan.trading_dashboard.finance.FinanceTransactionRecord;
import com.austinharlan.trading_dashboard.service.FinanceInsightsService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FinanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class FinanceControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private FinanceInsightsService financeInsightsService;

  @Test
  void getFinanceSummaryReturnsSummary() throws Exception {
    Instant asOf = Instant.parse("2024-05-15T12:00:00Z");
    FinanceSummaryData summary =
        new FinanceSummaryData(
            new BigDecimal("1500.00"),
            new BigDecimal("100.00"),
            new BigDecimal("3100.00"),
            asOf);
    when(financeInsightsService.getSummary()).thenReturn(summary);

    mockMvc
        .perform(get("/api/finance/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.month_to_date_spend").value(1500.00))
        .andExpect(jsonPath("$.average_daily_spend").value(100.00))
        .andExpect(jsonPath("$.projected_month_end_spend").value(3100.00))
        .andExpect(jsonPath("$.as_of").exists());
  }

  @Test
  void listFinanceTransactionsReturnsTransactions() throws Exception {
    Instant postedAt = Instant.parse("2024-05-10T10:00:00Z");
    List<FinanceTransactionRecord> transactions =
        List.of(
            new FinanceTransactionRecord(
                "txn-001",
                postedAt,
                "Grocery Store",
                new BigDecimal("-75.50"),
                "groceries",
                "Weekly shopping"),
            new FinanceTransactionRecord(
                "txn-002",
                postedAt.minusSeconds(3600),
                "Coffee Shop",
                new BigDecimal("-5.00"),
                "dining",
                null));
    when(financeInsightsService.listTransactions(isNull(), isNull())).thenReturn(transactions);

    mockMvc
        .perform(get("/api/finance/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactions").isArray())
        .andExpect(jsonPath("$.transactions.length()").value(2))
        .andExpect(jsonPath("$.transactions[0].id").value("txn-001"))
        .andExpect(jsonPath("$.transactions[0].description").value("Grocery Store"))
        .andExpect(jsonPath("$.transactions[0].amount").value(-75.50))
        .andExpect(jsonPath("$.transactions[0].category").value("groceries"))
        .andExpect(jsonPath("$.transactions[0].notes").value("Weekly shopping"))
        .andExpect(jsonPath("$.transactions[1].notes").doesNotExist())
        .andExpect(jsonPath("$.as_of").exists());
  }

  @Test
  void listFinanceTransactionsReturnsNoContentWhenEmpty() throws Exception {
    when(financeInsightsService.listTransactions(any(), any())).thenReturn(Collections.emptyList());

    mockMvc.perform(get("/api/finance/transactions")).andExpect(status().isNoContent());
  }

  @Test
  void listFinanceTransactionsFiltersByCategory() throws Exception {
    Instant postedAt = Instant.parse("2024-05-10T10:00:00Z");
    List<FinanceTransactionRecord> groceryTransactions =
        List.of(
            new FinanceTransactionRecord(
                "txn-001",
                postedAt,
                "Grocery Store",
                new BigDecimal("-75.50"),
                "groceries",
                null));
    when(financeInsightsService.listTransactions(isNull(), eq("groceries")))
        .thenReturn(groceryTransactions);

    mockMvc
        .perform(get("/api/finance/transactions").param("category", "groceries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactions.length()").value(1))
        .andExpect(jsonPath("$.transactions[0].category").value("groceries"));
  }

  @Test
  void listFinanceTransactionsRespectsLimit() throws Exception {
    Instant postedAt = Instant.parse("2024-05-10T10:00:00Z");
    List<FinanceTransactionRecord> limitedTransactions =
        List.of(
            new FinanceTransactionRecord(
                "txn-001", postedAt, "Store A", new BigDecimal("-50.00"), "shopping", null));
    when(financeInsightsService.listTransactions(eq(1), isNull())).thenReturn(limitedTransactions);

    mockMvc
        .perform(get("/api/finance/transactions").param("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactions.length()").value(1));
  }
}
