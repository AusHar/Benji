package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.persistence.TradeEntity;
import com.austinharlan.trading_dashboard.service.TradeService;
import com.austinharlan.trading_dashboard.trades.ClosedTrade;
import com.austinharlan.tradingdashboard.api.TradesApi;
import com.austinharlan.tradingdashboard.dto.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TradeController implements TradesApi {

  private final TradeService tradeService;

  public TradeController(TradeService tradeService) {
    this.tradeService = tradeService;
  }

  @Override
  public ResponseEntity<Trade> logTrade(@Valid LogTradeRequest logTradeRequest) {
    TradeEntity entity =
        tradeService.logTrade(
            logTradeRequest.getTicker().toUpperCase().strip(),
            logTradeRequest.getSide().getValue(),
            BigDecimal.valueOf(logTradeRequest.getQuantity()),
            BigDecimal.valueOf(logTradeRequest.getPricePerShare()),
            logTradeRequest.getTradeDate(),
            logTradeRequest.getNotes());
    return ResponseEntity.status(201).body(toDto(entity));
  }

  @Override
  public ResponseEntity<TradeListResponse> listTrades(
      String ticker, String side, LocalDate from, LocalDate to) {
    String normalizedTicker = ticker != null ? ticker.toUpperCase().strip() : null;
    List<TradeEntity> trades = tradeService.listTrades(normalizedTicker, side, from, to);
    TradeListResponse response =
        new TradeListResponse()
            .trades(trades.stream().map(this::toDto).toList());
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<Trade> getTrade(Long id) {
    return ResponseEntity.ok(toDto(tradeService.getTrade(id)));
  }

  @Override
  public ResponseEntity<Void> deleteTrade(Long id) {
    tradeService.deleteTrade(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<TradeStats> getTradeStats() {
    TradeService.TradeStats stats = tradeService.getStats();
    TradeStats dto =
        new TradeStats()
            .totalTrades(stats.totalTrades())
            .wins(stats.wins())
            .losses(stats.losses())
            .winRate(stats.winRate())
            .totalPnl(stats.totalPnl().doubleValue())
            .currentStreak(stats.currentStreak())
            .currentStreakType(
                TradeStats.CurrentStreakTypeEnum.fromValue(stats.currentStreakType()))
            .bestWinStreak(stats.bestWinStreak())
            .bestLossStreak(stats.bestLossStreak())
            .avgHoldDays(stats.avgHoldDays())
            .topTickers(
                stats.topTickers().stream()
                    .map(
                        tp ->
                            new TickerPnl()
                                .ticker(tp.ticker())
                                .pnl(tp.pnl().doubleValue())
                                .tradeCount(tp.tradeCount()))
                    .toList());
    return ResponseEntity.ok(dto);
  }

  @Override
  public ResponseEntity<ClosedTradeListResponse> listClosedTrades() {
    List<ClosedTrade> closed = tradeService.getClosedTrades();
    ClosedTradeListResponse response =
        new ClosedTradeListResponse()
            .closedTrades(
                closed.stream()
                    .map(
                        ct ->
                            new com.austinharlan.tradingdashboard.dto.ClosedTrade()
                                .ticker(ct.ticker())
                                .quantity(ct.quantity().doubleValue())
                                .buyPrice(ct.buyPrice().doubleValue())
                                .sellPrice(ct.sellPrice().doubleValue())
                                .buyDate(ct.buyDate())
                                .sellDate(ct.sellDate())
                                .pnl(ct.pnl().doubleValue())
                                .pnlPercent(ct.pnlPercent().doubleValue())
                                .holdDays((int) ct.holdDays()))
                    .toList());
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<PnlHistoryResponse> getPnlHistory() {
    List<TradeService.PnlHistoryEntry> entries = tradeService.getPnlHistory();
    PnlHistoryResponse response =
        new PnlHistoryResponse()
            .entries(
                entries.stream()
                    .map(
                        e ->
                            new com.austinharlan.tradingdashboard.dto.PnlHistoryEntry()
                                .date(e.date())
                                .pnl(e.pnl().doubleValue())
                                .cumulativePnl(e.cumulativePnl().doubleValue()))
                    .toList());
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<TradeCalendarResponse> getTradeCalendar() {
    List<TradeService.TradeCalendarEntry> entries = tradeService.getTradeCalendar();
    TradeCalendarResponse response =
        new TradeCalendarResponse()
            .entries(
                entries.stream()
                    .map(
                        e ->
                            new com.austinharlan.tradingdashboard.dto.TradeCalendarEntry()
                                .date(e.date())
                                .pnl(e.pnl().doubleValue())
                                .tradeCount(e.tradeCount()))
                    .toList());
    return ResponseEntity.ok(response);
  }

  private Trade toDto(TradeEntity entity) {
    return new Trade()
        .id(entity.getId())
        .ticker(entity.getTicker())
        .side(Trade.SideEnum.fromValue(entity.getSide()))
        .quantity(entity.getQuantity().doubleValue())
        .pricePerShare(entity.getPricePerShare().doubleValue())
        .tradeDate(entity.getTradeDate())
        .notes(entity.getNotes())
        .createdAt(OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC));
  }
}
