package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.finance.FinanceCategoryRecord;
import com.austinharlan.trading_dashboard.finance.FinanceSummaryData;
import com.austinharlan.trading_dashboard.finance.FinanceTransactionRecord;
import com.austinharlan.trading_dashboard.service.FinanceInsightsService;
import com.austinharlan.tradingdashboard.api.FinanceApi;
import com.austinharlan.tradingdashboard.dto.AddCategoryRequest;
import com.austinharlan.tradingdashboard.dto.AddTransactionRequest;
import com.austinharlan.tradingdashboard.dto.FinanceCategoriesResponse;
import com.austinharlan.tradingdashboard.dto.FinanceCategory;
import com.austinharlan.tradingdashboard.dto.FinanceSummary;
import com.austinharlan.tradingdashboard.dto.FinanceTransaction;
import com.austinharlan.tradingdashboard.dto.FinanceTransactionsResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FinanceController implements FinanceApi {
  private final FinanceInsightsService financeInsightsService;

  @Override
  public ResponseEntity<FinanceSummary> getFinanceSummary() {
    FinanceSummaryData summary = financeInsightsService.getSummary();
    FinanceSummary dto =
        new FinanceSummary()
            .monthToDateSpend(summary.monthToDateSpend().doubleValue())
            .averageDailySpend(summary.averageDailySpend().doubleValue())
            .projectedMonthEndSpend(summary.projectedMonthEndSpend().doubleValue())
            .asOf(OffsetDateTime.ofInstant(summary.asOf(), ZoneOffset.UTC));
    return ResponseEntity.ok(dto);
  }

  @Override
  public ResponseEntity<FinanceTransactionsResponse> listFinanceTransactions(
      Integer limit, String category) {
    List<FinanceTransactionRecord> transactions =
        financeInsightsService.listTransactions(limit, category);
    if (transactions.isEmpty()) {
      return ResponseEntity.noContent().build();
    }

    List<FinanceTransaction> payload = transactions.stream().map(this::toDto).toList();
    FinanceTransactionsResponse response =
        new FinanceTransactionsResponse()
            .asOf(OffsetDateTime.now(ZoneOffset.UTC))
            .transactions(payload);
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<FinanceTransaction> addFinanceTransaction(AddTransactionRequest request) {
    FinanceTransactionRecord created =
        financeInsightsService.createTransaction(
            request.getPostedAt() != null ? request.getPostedAt().toInstant() : null,
            request.getDescription(),
            request.getAmount() != null ? BigDecimal.valueOf(request.getAmount()) : null,
            request.getCategory(),
            request.getNotes());
    return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
  }

  @Override
  public ResponseEntity<FinanceCategoriesResponse> listFinanceCategories() {
    List<FinanceCategory> categories =
        financeInsightsService.listCategories().stream().map(this::toDto).toList();
    return ResponseEntity.ok(new FinanceCategoriesResponse().categories(categories));
  }

  @Override
  public ResponseEntity<FinanceCategory> addFinanceCategory(AddCategoryRequest request) {
    FinanceCategoryRecord created = financeInsightsService.createCategory(request.getLabel());
    return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
  }

  @Override
  public ResponseEntity<Void> deleteFinanceCategory(String slug) {
    boolean deleted = financeInsightsService.deleteCategory(slug);
    if (!deleted) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.noContent().build();
  }

  private FinanceTransaction toDto(FinanceTransactionRecord record) {
    OffsetDateTime postedAt =
        record.postedAt() != null
            ? OffsetDateTime.ofInstant(record.postedAt(), ZoneOffset.UTC)
            : OffsetDateTime.now(ZoneOffset.UTC);
    double amount = record.amount() != null ? record.amount().doubleValue() : 0D;

    FinanceTransaction dto =
        new FinanceTransaction()
            .id(record.id())
            .postedAt(postedAt)
            .description(record.description())
            .amount(amount)
            .category(record.category());
    if (record.notes() != null) {
      dto.setNotes(record.notes());
    }
    return dto;
  }

  private FinanceCategory toDto(FinanceCategoryRecord record) {
    return new FinanceCategory().slug(record.slug()).label(record.label());
  }
}
