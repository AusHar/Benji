package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.config.UserContext;
import com.austinharlan.trading_dashboard.persistence.TradeEntity;
import com.austinharlan.trading_dashboard.persistence.TradeRepository;
import com.austinharlan.trading_dashboard.trades.ClosedTrade;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultTradeService implements TradeService {

  private final TradeRepository repository;

  public DefaultTradeService(TradeRepository repository) {
    this.repository = repository;
  }

  @Override
  public TradeEntity logTrade(
      String ticker,
      String side,
      BigDecimal quantity,
      BigDecimal pricePerShare,
      @Nullable LocalDate tradeDate,
      @Nullable String notes) {
    long userId = UserContext.current().userId();
    LocalDate date = tradeDate != null ? tradeDate : LocalDate.now();
    TradeEntity entity =
        new TradeEntity(userId, ticker, side, quantity, pricePerShare, date, notes);
    return repository.save(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public List<TradeEntity> listTrades(
      @Nullable String ticker,
      @Nullable String side,
      @Nullable LocalDate from,
      @Nullable LocalDate to) {
    long userId = UserContext.current().userId();
    if (ticker == null && side == null && from == null && to == null) {
      return repository.findAllByUserIdOrderByTradeDateDescCreatedAtDesc(userId);
    }
    return repository.findFilteredByUserId(userId, ticker, side, from, to);
  }

  @Override
  @Transactional(readOnly = true)
  public TradeEntity getTrade(long id) {
    long userId = UserContext.current().userId();
    TradeEntity entity = repository.findById(id).orElseThrow(() -> notFound(id));
    if (!entity.getUserId().equals(userId)) {
      throw new EntityNotFoundException("Trade not found: " + id);
    }
    return entity;
  }

  @Override
  public void deleteTrade(long id) {
    long userId = UserContext.current().userId();
    TradeEntity entity = repository.findById(id).orElseThrow(() -> notFound(id));
    if (!entity.getUserId().equals(userId)) {
      throw new EntityNotFoundException("Trade not found: " + id);
    }
    repository.deleteById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ClosedTrade> getClosedTrades() {
    long userId = UserContext.current().userId();
    List<TradeEntity> all = repository.findAllChronologicalByUserId(userId);
    return computeClosedTrades(all);
  }

  @Override
  @Transactional(readOnly = true)
  public TradeStats getStats() {
    List<ClosedTrade> closed = getClosedTrades();
    if (closed.isEmpty()) {
      return new TradeStats(0, 0, 0, 0.0, BigDecimal.ZERO, 0, "NONE", 0, 0, 0.0, List.of());
    }

    int wins = 0, losses = 0;
    BigDecimal totalPnl = BigDecimal.ZERO;
    long totalHoldDays = 0;
    int bestWinStreak = 0, bestLossStreak = 0;
    int runWin = 0, runLoss = 0;

    List<ClosedTrade> sorted =
        closed.stream().sorted(Comparator.comparing(ClosedTrade::sellDate)).toList();

    for (ClosedTrade ct : sorted) {
      totalPnl = totalPnl.add(ct.pnl());
      totalHoldDays += ct.holdDays();
      if (ct.pnl().compareTo(BigDecimal.ZERO) > 0) {
        wins++;
        runWin++;
        runLoss = 0;
        bestWinStreak = Math.max(bestWinStreak, runWin);
      } else {
        losses++;
        runLoss++;
        runWin = 0;
        bestLossStreak = Math.max(bestLossStreak, runLoss);
      }
    }

    int total = wins + losses;
    double winRate = total > 0 ? (double) wins / total * 100.0 : 0.0;
    double avgHold = total > 0 ? (double) totalHoldDays / total : 0.0;
    int currentStreak = Math.max(runWin, runLoss);
    String currentStreakType = runWin > 0 ? "WIN" : (runLoss > 0 ? "LOSS" : "NONE");

    Map<String, BigDecimal> pnlByTicker = new LinkedHashMap<>();
    Map<String, Integer> countByTicker = new LinkedHashMap<>();
    for (ClosedTrade ct : closed) {
      pnlByTicker.merge(ct.ticker(), ct.pnl(), BigDecimal::add);
      countByTicker.merge(ct.ticker(), 1, Integer::sum);
    }
    List<TickerPnl> topTickers =
        pnlByTicker.entrySet().stream()
            .sorted(
                Comparator.comparing(
                    (Map.Entry<String, BigDecimal> e) -> e.getValue().abs(),
                    Comparator.reverseOrder()))
            .limit(5)
            .map(e -> new TickerPnl(e.getKey(), e.getValue(), countByTicker.get(e.getKey())))
            .toList();

    return new TradeStats(
        total,
        wins,
        losses,
        winRate,
        totalPnl,
        currentStreak,
        currentStreakType,
        bestWinStreak,
        bestLossStreak,
        avgHold,
        topTickers);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PnlHistoryEntry> getPnlHistory() {
    List<ClosedTrade> closed = getClosedTrades();
    List<ClosedTrade> sorted =
        closed.stream().sorted(Comparator.comparing(ClosedTrade::sellDate)).toList();
    List<PnlHistoryEntry> history = new ArrayList<>();
    BigDecimal cumulative = BigDecimal.ZERO;
    for (ClosedTrade ct : sorted) {
      cumulative = cumulative.add(ct.pnl());
      history.add(new PnlHistoryEntry(ct.sellDate(), ct.pnl(), cumulative));
    }
    return history;
  }

  @Override
  @Transactional(readOnly = true)
  public List<TradeCalendarEntry> getTradeCalendar() {
    List<ClosedTrade> closed = getClosedTrades();
    Map<LocalDate, BigDecimal> pnlByDate = new LinkedHashMap<>();
    Map<LocalDate, Integer> countByDate = new LinkedHashMap<>();
    for (ClosedTrade ct : closed) {
      pnlByDate.merge(ct.sellDate(), ct.pnl(), BigDecimal::add);
      countByDate.merge(ct.sellDate(), 1, Integer::sum);
    }
    return pnlByDate.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> new TradeCalendarEntry(e.getKey(), e.getValue(), countByDate.get(e.getKey())))
        .toList();
  }

  static List<ClosedTrade> computeClosedTrades(List<TradeEntity> trades) {
    Map<String, List<TradeEntity>> byTicker =
        trades.stream()
            .collect(
                Collectors.groupingBy(
                    TradeEntity::getTicker, LinkedHashMap::new, Collectors.toList()));
    List<ClosedTrade> result = new ArrayList<>();
    for (Map.Entry<String, List<TradeEntity>> entry : byTicker.entrySet()) {
      String ticker = entry.getKey();
      Deque<BuyLot> buyQueue = new ArrayDeque<>();
      for (TradeEntity t : entry.getValue()) {
        if ("BUY".equals(t.getSide())) {
          buyQueue.addLast(new BuyLot(t.getQuantity(), t.getPricePerShare(), t.getTradeDate()));
        } else {
          BigDecimal sellRemaining = t.getQuantity();
          while (sellRemaining.compareTo(BigDecimal.ZERO) > 0 && !buyQueue.isEmpty()) {
            BuyLot lot = buyQueue.peekFirst();
            BigDecimal matched = sellRemaining.min(lot.remaining);
            BigDecimal pnl = matched.multiply(t.getPricePerShare().subtract(lot.price));
            BigDecimal pnlPct =
                lot.price.compareTo(BigDecimal.ZERO) > 0
                    ? t.getPricePerShare()
                        .subtract(lot.price)
                        .divide(lot.price, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            long holdDays = ChronoUnit.DAYS.between(lot.date, t.getTradeDate());
            result.add(
                new ClosedTrade(
                    ticker,
                    matched,
                    lot.price,
                    t.getPricePerShare(),
                    lot.date,
                    t.getTradeDate(),
                    pnl,
                    pnlPct,
                    holdDays));
            lot.remaining = lot.remaining.subtract(matched);
            sellRemaining = sellRemaining.subtract(matched);
            if (lot.remaining.compareTo(BigDecimal.ZERO) == 0) {
              buyQueue.pollFirst();
            }
          }
        }
      }
    }
    return result;
  }

  private static EntityNotFoundException notFound(long id) {
    return new EntityNotFoundException("Trade not found: " + id);
  }

  private static class BuyLot {
    BigDecimal remaining;
    final BigDecimal price;
    final LocalDate date;

    BuyLot(BigDecimal qty, BigDecimal price, LocalDate date) {
      this.remaining = qty;
      this.price = price;
      this.date = date;
    }
  }
}
