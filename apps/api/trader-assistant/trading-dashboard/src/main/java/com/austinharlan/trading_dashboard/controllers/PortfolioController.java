package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.portfolio.PortfolioHolding;
import com.austinharlan.trading_dashboard.portfolio.PortfolioSnapshot;
import com.austinharlan.trading_dashboard.service.PortfolioService;
import com.austinharlan.tradingdashboard.api.PortfolioApi;
import com.austinharlan.tradingdashboard.dto.CreatePortfolioPositionRequest;
import com.austinharlan.tradingdashboard.dto.PortfolioPosition;
import com.austinharlan.tradingdashboard.dto.PortfolioPositionsResponse;
import com.austinharlan.tradingdashboard.dto.PortfolioSummary;
import com.austinharlan.tradingdashboard.dto.UpdatePortfolioPositionRequest;
import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PortfolioController implements PortfolioApi {
  private final PortfolioService portfolioService;

  @Override
  public ResponseEntity<PortfolioPositionsResponse> listPortfolioPositions() {
    List<PortfolioHolding> holdings = portfolioService.listHoldings();
    if (holdings.isEmpty()) {
      return ResponseEntity.noContent().build();
    }

    List<PortfolioPosition> positions = holdings.stream().map(this::toDto).toList();

    PortfolioPositionsResponse response =
        new PortfolioPositionsResponse()
            .asOf(OffsetDateTime.now(ZoneOffset.UTC))
            .positions(positions);
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<PortfolioSummary> getPortfolioSummary() {
    return portfolioService
        .summarize()
        .map(this::toDto)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @Override
  public ResponseEntity<PortfolioPosition> getPortfolioPosition(String ticker) {
    return portfolioService
        .findByTicker(ticker)
        .map(this::toDto)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<PortfolioPosition> createPortfolioPosition(
      CreatePortfolioPositionRequest request) {
    if (portfolioService.existsByTicker(request.getTicker())) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    PortfolioHolding created =
        portfolioService.create(
            request.getTicker(),
            BigDecimal.valueOf(request.getQuantity()),
            BigDecimal.valueOf(request.getCostBasis()));

    PortfolioPosition dto = toDto(created);
    return ResponseEntity.created(URI.create("/api/portfolio/positions/" + created.ticker()))
        .body(dto);
  }

  @Override
  public ResponseEntity<PortfolioPosition> updatePortfolioPosition(
      String ticker, UpdatePortfolioPositionRequest request) {
    return portfolioService
        .update(
            ticker,
            request.getQuantity() != null ? BigDecimal.valueOf(request.getQuantity()) : null,
            request.getCostBasis() != null ? BigDecimal.valueOf(request.getCostBasis()) : null)
        .map(this::toDto)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<Void> deletePortfolioPosition(String ticker) {
    if (portfolioService.delete(ticker)) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.notFound().build();
  }

  private PortfolioPosition toDto(PortfolioHolding holding) {
    return new PortfolioPosition()
        .ticker(holding.ticker())
        .quantity(holding.quantity().doubleValue())
        .costBasis(holding.costBasis().doubleValue());
  }

  private PortfolioSummary toDto(PortfolioSnapshot snapshot) {
    return new PortfolioSummary()
        .positionsCount(snapshot.positionsCount())
        .totalQuantity(snapshot.totalQuantity().doubleValue())
        .totalCostBasis(snapshot.totalCostBasis().doubleValue())
        .asOf(OffsetDateTime.now(ZoneOffset.UTC));
  }
}
