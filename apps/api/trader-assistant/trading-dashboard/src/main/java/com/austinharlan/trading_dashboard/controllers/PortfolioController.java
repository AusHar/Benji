package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.portfolio.PortfolioHolding;
import com.austinharlan.trading_dashboard.portfolio.PortfolioSnapshot;
import com.austinharlan.trading_dashboard.service.PortfolioService;
import com.austinharlan.tradingdashboard.api.PortfolioApi;
import com.austinharlan.tradingdashboard.dto.AddPositionRequest;
import com.austinharlan.tradingdashboard.dto.PortfolioPosition;
import com.austinharlan.tradingdashboard.dto.PortfolioPositionsResponse;
import com.austinharlan.tradingdashboard.dto.PortfolioSummary;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
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
  public ResponseEntity<PortfolioPosition> addPortfolioPosition(
      @Valid @RequestBody AddPositionRequest request) {
    PortfolioHolding holding =
        portfolioService.addHolding(
            request.getTicker().toUpperCase().strip(),
            BigDecimal.valueOf(request.getQuantity()),
            BigDecimal.valueOf(request.getPricePerShare()));
    return ResponseEntity.status(201).body(toDto(holding));
  }

  @Override
  public ResponseEntity<Void> deletePortfolioPosition(String ticker) {
    portfolioService.deleteHolding(ticker.toUpperCase().strip());
    return ResponseEntity.noContent().build();
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
