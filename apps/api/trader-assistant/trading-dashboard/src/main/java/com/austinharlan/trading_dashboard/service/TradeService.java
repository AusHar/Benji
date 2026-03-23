package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.persistence.TradeEntity;
import com.austinharlan.trading_dashboard.trades.ClosedTrade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.lang.Nullable;

public interface TradeService {

  TradeEntity logTrade(
      String ticker,
      String side,
      BigDecimal quantity,
      BigDecimal pricePerShare,
      @Nullable LocalDate tradeDate,
      @Nullable String notes);

  List<TradeEntity> listTrades(
      @Nullable String ticker,
      @Nullable String side,
      @Nullable LocalDate from,
      @Nullable LocalDate to);

  TradeEntity getTrade(long id);

  void deleteTrade(long id);

  List<ClosedTrade> getClosedTrades();

  TradeStats getStats();

  List<PnlHistoryEntry> getPnlHistory();

  List<TradeCalendarEntry> getTradeCalendar();

  record TradeStats(
      int totalTrades,
      int wins,
      int losses,
      double winRate,
      BigDecimal totalPnl,
      int currentStreak,
      String currentStreakType,
      int bestWinStreak,
      int bestLossStreak,
      double avgHoldDays,
      List<TickerPnl> topTickers) {}

  record TickerPnl(String ticker, BigDecimal pnl, int tradeCount) {}

  record PnlHistoryEntry(LocalDate date, BigDecimal pnl, BigDecimal cumulativePnl) {}

  record TradeCalendarEntry(LocalDate date, BigDecimal pnl, int tradeCount) {}
}
