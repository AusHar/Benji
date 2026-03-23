package com.austinharlan.trading_dashboard.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.austinharlan.trading_dashboard.persistence.TradeEntity;
import com.austinharlan.trading_dashboard.persistence.TradeRepository;
import com.austinharlan.trading_dashboard.trades.ClosedTrade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTradeServiceTest {

  private TradeRepository repository;
  private DefaultTradeService service;

  @BeforeEach
  void setUp() {
    repository = mock(TradeRepository.class);
    service = new DefaultTradeService(repository);
  }

  private TradeEntity trade(String ticker, String side, double qty, double price, String date) {
    return new TradeEntity(
        ticker,
        side,
        BigDecimal.valueOf(qty),
        BigDecimal.valueOf(price),
        LocalDate.parse(date),
        null);
  }

  @Test
  void fifo_singleBuyAndSell_producesOneClosedTrade() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 10, 150.00, "2026-01-01"),
                trade("AAPL", "SELL", 10, 200.00, "2026-02-01")));
    List<ClosedTrade> closed = service.getClosedTrades();
    assertThat(closed).hasSize(1);
    ClosedTrade ct = closed.get(0);
    assertThat(ct.ticker()).isEqualTo("AAPL");
    assertThat(ct.quantity()).isEqualByComparingTo("10");
    assertThat(ct.buyPrice()).isEqualByComparingTo("150.00");
    assertThat(ct.sellPrice()).isEqualByComparingTo("200.00");
    assertThat(ct.pnl()).isEqualByComparingTo("500.00");
    assertThat(ct.holdDays()).isEqualTo(31);
  }

  @Test
  void fifo_partialSell_closesOnlyMatchedQuantity() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 100, 150.00, "2026-01-01"),
                trade("AAPL", "SELL", 50, 200.00, "2026-02-01")));
    List<ClosedTrade> closed = service.getClosedTrades();
    assertThat(closed).hasSize(1);
    assertThat(closed.get(0).quantity()).isEqualByComparingTo("50");
    assertThat(closed.get(0).pnl()).isEqualByComparingTo("2500.00");
  }

  @Test
  void fifo_largeSellConsumesMultipleBuys() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 30, 100.00, "2026-01-01"),
                trade("AAPL", "BUY", 20, 120.00, "2026-01-15"),
                trade("AAPL", "SELL", 50, 150.00, "2026-02-01")));
    List<ClosedTrade> closed = service.getClosedTrades();
    assertThat(closed).hasSize(2);
    assertThat(closed.get(0).quantity()).isEqualByComparingTo("30");
    assertThat(closed.get(0).buyPrice()).isEqualByComparingTo("100.00");
    assertThat(closed.get(0).pnl()).isEqualByComparingTo("1500.00");
    assertThat(closed.get(1).quantity()).isEqualByComparingTo("20");
    assertThat(closed.get(1).buyPrice()).isEqualByComparingTo("120.00");
    assertThat(closed.get(1).pnl()).isEqualByComparingTo("600.00");
  }

  @Test
  void fifo_sellWithNoBuys_producesNoClosedTrade() {
    when(repository.findAllChronological())
        .thenReturn(List.of(trade("AAPL", "SELL", 10, 200.00, "2026-02-01")));
    List<ClosedTrade> closed = service.getClosedTrades();
    assertThat(closed).isEmpty();
  }

  @Test
  void fifo_multipleTickersMatchedIndependently() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 10, 150.00, "2026-01-01"),
                trade("MSFT", "BUY", 5, 300.00, "2026-01-01"),
                trade("AAPL", "SELL", 10, 180.00, "2026-02-01"),
                trade("MSFT", "SELL", 5, 350.00, "2026-02-01")));
    List<ClosedTrade> closed = service.getClosedTrades();
    assertThat(closed).hasSize(2);
    assertThat(closed).extracting(ClosedTrade::ticker).containsExactlyInAnyOrder("AAPL", "MSFT");
  }

  @Test
  void stats_winRateCalculation() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 10, 100.00, "2026-01-01"),
                trade("AAPL", "SELL", 10, 150.00, "2026-01-10"),
                trade("MSFT", "BUY", 10, 200.00, "2026-01-01"),
                trade("MSFT", "SELL", 10, 180.00, "2026-01-10"),
                trade("NVDA", "BUY", 10, 50.00, "2026-01-01"),
                trade("NVDA", "SELL", 10, 80.00, "2026-01-10")));
    TradeService.TradeStats stats = service.getStats();
    assertThat(stats.totalTrades()).isEqualTo(3);
    assertThat(stats.wins()).isEqualTo(2);
    assertThat(stats.losses()).isEqualTo(1);
    assertThat(stats.winRate()).isCloseTo(66.67, within(0.1));
  }

  @Test
  void stats_streakTracking() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("A", "BUY", 1, 10, "2026-01-01"),
                trade("A", "SELL", 1, 20, "2026-01-10"),
                trade("B", "BUY", 1, 10, "2026-01-11"),
                trade("B", "SELL", 1, 20, "2026-01-20"),
                trade("C", "BUY", 1, 20, "2026-01-21"),
                trade("C", "SELL", 1, 10, "2026-01-30"),
                trade("D", "BUY", 1, 10, "2026-02-01"),
                trade("D", "SELL", 1, 15, "2026-02-10")));
    TradeService.TradeStats stats = service.getStats();
    assertThat(stats.currentStreak()).isEqualTo(1);
    assertThat(stats.currentStreakType()).isEqualTo("WIN");
    assertThat(stats.bestWinStreak()).isEqualTo(2);
    assertThat(stats.bestLossStreak()).isEqualTo(1);
  }

  @Test
  void stats_avgHoldDays() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 10, 100, "2026-01-01"),
                trade("AAPL", "SELL", 10, 150, "2026-01-11"),
                trade("MSFT", "BUY", 10, 200, "2026-01-01"),
                trade("MSFT", "SELL", 10, 250, "2026-01-21")));
    TradeService.TradeStats stats = service.getStats();
    assertThat(stats.avgHoldDays()).isCloseTo(15.0, within(0.1));
  }

  @Test
  void stats_topTickersSortedByAbsolutePnl() {
    when(repository.findAllChronological())
        .thenReturn(
            List.of(
                trade("AAPL", "BUY", 10, 100, "2026-01-01"),
                trade("AAPL", "SELL", 10, 110, "2026-01-10"),
                trade("MSFT", "BUY", 10, 200, "2026-01-01"),
                trade("MSFT", "SELL", 10, 150, "2026-01-10"),
                trade("NVDA", "BUY", 10, 50, "2026-01-01"),
                trade("NVDA", "SELL", 10, 80, "2026-01-10")));
    TradeService.TradeStats stats = service.getStats();
    assertThat(stats.topTickers()).hasSize(3);
    assertThat(stats.topTickers().get(0).ticker()).isEqualTo("MSFT");
    assertThat(stats.topTickers().get(1).ticker()).isEqualTo("NVDA");
    assertThat(stats.topTickers().get(2).ticker()).isEqualTo("AAPL");
  }

  @Test
  void stats_emptyTrades_returnsZeros() {
    when(repository.findAllChronological()).thenReturn(List.of());
    TradeService.TradeStats stats = service.getStats();
    assertThat(stats.totalTrades()).isEqualTo(0);
    assertThat(stats.winRate()).isEqualTo(0.0);
    assertThat(stats.currentStreakType()).isEqualTo("NONE");
  }
}
