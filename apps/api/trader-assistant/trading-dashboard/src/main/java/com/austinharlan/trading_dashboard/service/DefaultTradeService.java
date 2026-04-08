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
      @Nullable BigDecimal quantity,
      @Nullable BigDecimal pricePerShare,
      @Nullable LocalDate tradeDate,
      @Nullable String notes,
      @Nullable String assetType,
      @Nullable String optionType,
      @Nullable BigDecimal strikePrice,
      @Nullable LocalDate expirationDate) {

    long userId = UserContext.current().userId();
    LocalDate date = tradeDate != null ? tradeDate : LocalDate.now();
    String type = assetType != null ? assetType : "EQUITY";
    int multiplier = "OPTION".equals(type) ? 100 : 1;

    validate(side, type, quantity, pricePerShare, optionType, strikePrice, expirationDate);

    if ("EXPIRE".equals(side)) {
      return handleExpire(userId, ticker, date, notes, optionType, strikePrice, expirationDate);
    }
    if ("EXERCISE".equals(side)) {
      return handleExercise(
          userId, ticker, date, notes, quantity, optionType, strikePrice, expirationDate);
    }

    TradeEntity entity =
        new TradeEntity(
            userId,
            ticker,
            side,
            quantity,
            pricePerShare,
            date,
            notes,
            type,
            optionType,
            strikePrice,
            expirationDate,
            multiplier);
    return repository.save(entity);
  }

  private void validate(
      String side,
      String assetType,
      @Nullable BigDecimal quantity,
      @Nullable BigDecimal pricePerShare,
      @Nullable String optionType,
      @Nullable BigDecimal strikePrice,
      @Nullable LocalDate expirationDate) {

    if ("EXPIRE".equals(side) || "EXERCISE".equals(side)) {
      if (!"OPTION".equals(assetType)) {
        throw new IllegalArgumentException(side + " is only valid for OPTION trades");
      }
      requireOptionFields(optionType, strikePrice, expirationDate);
      if ("EXERCISE".equals(side)) {
        requirePositive(quantity, "quantity");
      }
      return;
    }

    // BUY or SELL
    requirePositive(quantity, "quantity");
    requirePositive(pricePerShare, "pricePerShare");

    if ("OPTION".equals(assetType)) {
      requireOptionFields(optionType, strikePrice, expirationDate);
    } else {
      if (optionType != null || strikePrice != null || expirationDate != null) {
        throw new IllegalArgumentException("Option fields must be null for EQUITY trades");
      }
    }
  }

  private void requireOptionFields(
      @Nullable String optionType,
      @Nullable BigDecimal strikePrice,
      @Nullable LocalDate expirationDate) {
    if (optionType == null || strikePrice == null || expirationDate == null) {
      throw new IllegalArgumentException(
          "optionType, strikePrice, and expirationDate are required for OPTION trades");
    }
  }

  private void requirePositive(@Nullable BigDecimal value, String field) {
    if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException(field + " must be greater than 0");
    }
  }

  private TradeEntity handleExpire(
      long userId,
      String ticker,
      LocalDate date,
      @Nullable String notes,
      String optionType,
      BigDecimal strikePrice,
      LocalDate expirationDate) {

    List<TradeEntity> all = repository.findAllChronologicalByUserId(userId);
    ContractKey key = new ContractKey(ticker, "OPTION", optionType, strikePrice, expirationDate);
    BigDecimal remainingQty = computeRemainingQuantity(all, key);

    if (remainingQty.compareTo(BigDecimal.ZERO) == 0) {
      throw new IllegalArgumentException("No open lots found for this contract to expire");
    }

    TradeEntity expire =
        new TradeEntity(
            userId,
            ticker,
            "EXPIRE",
            remainingQty,
            BigDecimal.ZERO,
            date,
            notes,
            "OPTION",
            optionType,
            strikePrice,
            expirationDate,
            100);
    return repository.save(expire);
  }

  private TradeEntity handleExercise(
      long userId,
      String ticker,
      LocalDate date,
      @Nullable String notes,
      BigDecimal quantity,
      String optionType,
      BigDecimal strikePrice,
      LocalDate expirationDate) {

    // Determine if long or short position by checking which side has open lots
    List<TradeEntity> all = repository.findAllChronologicalByUserId(userId);
    ContractKey key = new ContractKey(ticker, "OPTION", optionType, strikePrice, expirationDate);
    BigDecimal remainingQty = computeRemainingQuantity(all, key);
    if (remainingQty.compareTo(BigDecimal.ZERO) == 0) {
      throw new IllegalArgumentException("No open lots found for this contract to exercise");
    }
    if (quantity.compareTo(remainingQty) > 0) {
      throw new IllegalArgumentException(
          "Exercise quantity " + quantity + " exceeds remaining open lots " + remainingQty);
    }
    boolean isLong = isLongPosition(all, key);

    // Save the option EXERCISE trade
    TradeEntity exercise =
        new TradeEntity(
            userId,
            ticker,
            "EXERCISE",
            quantity,
            BigDecimal.ZERO,
            date,
            notes,
            "OPTION",
            optionType,
            strikePrice,
            expirationDate,
            100);
    exercise = repository.save(exercise);

    // Determine linked equity side and create it
    BigDecimal shares = quantity.multiply(BigDecimal.valueOf(100));
    String equitySide;
    if ("CALL".equals(optionType)) {
      equitySide = isLong ? "BUY" : "SELL";
    } else {
      equitySide = isLong ? "SELL" : "BUY";
    }
    String equityNotes =
        String.format(
            "Auto-created from %s exercise on %s $%s%s %s",
            optionType,
            ticker,
            strikePrice.stripTrailingZeros().toPlainString(),
            "CALL".equals(optionType) ? "C" : "P",
            String.format("%tD", expirationDate));

    TradeEntity equity =
        new TradeEntity(userId, ticker, equitySide, shares, strikePrice, date, equityNotes);
    equity = repository.save(equity);

    // Link bidirectionally
    exercise.setLinkedTradeId(equity.getId());
    equity.setLinkedTradeId(exercise.getId());
    repository.save(exercise);
    repository.save(equity);

    return exercise;
  }

  private boolean isLongPosition(List<TradeEntity> allTrades, ContractKey key) {
    BigDecimal buyQty = BigDecimal.ZERO;
    BigDecimal sellQty = BigDecimal.ZERO;
    for (TradeEntity t : allTrades) {
      if (!key.matches(t)) continue;
      if ("BUY".equals(t.getSide())) {
        buyQty = buyQty.add(t.getQuantity());
      } else if ("SELL".equals(t.getSide())) {
        sellQty = sellQty.add(t.getQuantity());
      }
    }
    return buyQty.compareTo(sellQty) > 0;
  }

  private BigDecimal computeRemainingQuantity(List<TradeEntity> allTrades, ContractKey key) {
    BigDecimal buyQty = BigDecimal.ZERO;
    BigDecimal sellQty = BigDecimal.ZERO;
    for (TradeEntity t : allTrades) {
      if (!key.matches(t)) continue;
      String s = t.getSide();
      if ("BUY".equals(s)) {
        buyQty = buyQty.add(t.getQuantity());
      } else if ("SELL".equals(s)) {
        sellQty = sellQty.add(t.getQuantity());
      }
      // EXPIRE/EXERCISE already matched against open lots, so they close qty too
      if ("EXPIRE".equals(s) || "EXERCISE".equals(s)) {
        // These reduce the majority side
        if (buyQty.compareTo(sellQty) > 0) {
          sellQty = sellQty.add(t.getQuantity());
        } else {
          buyQty = buyQty.add(t.getQuantity());
        }
      }
    }
    return buyQty.subtract(sellQty).abs();
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

  // ── Bidirectional Lot Matcher ────────────────────────────────────────────

  static List<ClosedTrade> computeClosedTrades(List<TradeEntity> trades) {
    Map<ContractKey, List<TradeEntity>> byContract =
        trades.stream()
            .collect(
                Collectors.groupingBy(ContractKey::from, LinkedHashMap::new, Collectors.toList()));

    List<ClosedTrade> result = new ArrayList<>();
    for (Map.Entry<ContractKey, List<TradeEntity>> entry : byContract.entrySet()) {
      ContractKey key = entry.getKey();
      Deque<Lot> buyQueue = new ArrayDeque<>();
      Deque<Lot> sellQueue = new ArrayDeque<>();

      for (TradeEntity t : entry.getValue()) {
        String side = t.getSide();
        int mult = t.getMultiplier();

        if ("EXPIRE".equals(side)) {
          // Close ALL remaining lots at $0
          closeAllLots(buyQueue, t, key, mult, result);
          closeAllLots(sellQueue, t, key, mult, result);
          continue;
        }

        if ("EXERCISE".equals(side)) {
          // Close specified quantity from whichever side has open lots
          Deque<Lot> openQueue = !buyQueue.isEmpty() ? buyQueue : sellQueue;
          matchLots(
              openQueue, t.getQuantity(), BigDecimal.ZERO, t.getTradeDate(), key, mult, result);
          continue;
        }

        boolean isBuy = "BUY".equals(side);
        Deque<Lot> oppositeQueue = isBuy ? sellQueue : buyQueue;
        Deque<Lot> sameQueue = isBuy ? buyQueue : sellQueue;

        if (!oppositeQueue.isEmpty()) {
          // Closing: match against opposite side; capture any unmatched remainder
          BigDecimal unmatched =
              matchLots(
                  oppositeQueue,
                  t.getQuantity(),
                  t.getPricePerShare(),
                  t.getTradeDate(),
                  key,
                  mult,
                  result);
          if (unmatched.compareTo(BigDecimal.ZERO) > 0) {
            sameQueue.addLast(new Lot(unmatched, t.getPricePerShare(), t.getTradeDate(), isBuy));
          }
        } else {
          // Opening: add to same side queue
          sameQueue.addLast(
              new Lot(t.getQuantity(), t.getPricePerShare(), t.getTradeDate(), isBuy));
        }
      }
    }
    return result;
  }

  private static void closeAllLots(
      Deque<Lot> queue,
      TradeEntity closingTrade,
      ContractKey key,
      int multiplier,
      List<ClosedTrade> result) {
    while (!queue.isEmpty()) {
      Lot lot = queue.pollFirst();
      BigDecimal buyPrice = lot.isBuy ? lot.price : BigDecimal.ZERO;
      BigDecimal sellPrice = lot.isBuy ? BigDecimal.ZERO : lot.price;
      LocalDate buyDate = lot.isBuy ? lot.date : closingTrade.getTradeDate();
      LocalDate sellDate = lot.isBuy ? closingTrade.getTradeDate() : lot.date;
      BigDecimal pnl =
          sellPrice
              .subtract(buyPrice)
              .multiply(lot.remaining)
              .multiply(BigDecimal.valueOf(multiplier));
      BigDecimal pnlPct = computePnlPercent(buyPrice, sellPrice);
      long holdDays = Math.abs(ChronoUnit.DAYS.between(buyDate, sellDate));
      result.add(
          new ClosedTrade(
              key.ticker,
              lot.remaining,
              buyPrice,
              sellPrice,
              buyDate,
              sellDate,
              pnl,
              pnlPct,
              holdDays,
              key.assetType,
              key.optionType,
              key.strikePrice,
              key.expirationDate));
    }
  }

  private static BigDecimal matchLots(
      Deque<Lot> openQueue,
      BigDecimal closeQty,
      BigDecimal closePrice,
      LocalDate closeDate,
      ContractKey key,
      int multiplier,
      List<ClosedTrade> result) {
    BigDecimal remaining = closeQty;
    while (remaining.compareTo(BigDecimal.ZERO) > 0 && !openQueue.isEmpty()) {
      Lot lot = openQueue.peekFirst();
      BigDecimal matched = remaining.min(lot.remaining);

      BigDecimal buyPrice = lot.isBuy ? lot.price : closePrice;
      BigDecimal sellPrice = lot.isBuy ? closePrice : lot.price;
      LocalDate buyDate = lot.isBuy ? lot.date : closeDate;
      LocalDate sellDate = lot.isBuy ? closeDate : lot.date;
      BigDecimal pnl =
          sellPrice.subtract(buyPrice).multiply(matched).multiply(BigDecimal.valueOf(multiplier));
      BigDecimal pnlPct = computePnlPercent(buyPrice, sellPrice);
      long holdDays = Math.abs(ChronoUnit.DAYS.between(buyDate, sellDate));

      result.add(
          new ClosedTrade(
              key.ticker,
              matched,
              buyPrice,
              sellPrice,
              buyDate,
              sellDate,
              pnl,
              pnlPct,
              holdDays,
              key.assetType,
              key.optionType,
              key.strikePrice,
              key.expirationDate));

      lot.remaining = lot.remaining.subtract(matched);
      remaining = remaining.subtract(matched);
      if (lot.remaining.compareTo(BigDecimal.ZERO) == 0) {
        openQueue.pollFirst();
      }
    }
    return remaining;
  }

  private static BigDecimal computePnlPercent(BigDecimal buyPrice, BigDecimal sellPrice) {
    if (buyPrice.compareTo(BigDecimal.ZERO) == 0) {
      // Short position: percent based on sell (open) price
      if (sellPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
      return sellPrice
          .subtract(buyPrice)
          .divide(sellPrice, 4, RoundingMode.HALF_UP)
          .multiply(BigDecimal.valueOf(100));
    }
    return sellPrice
        .subtract(buyPrice)
        .divide(buyPrice, 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100));
  }

  private static EntityNotFoundException notFound(long id) {
    return new EntityNotFoundException("Trade not found: " + id);
  }

  // ── Inner types ──────────────────────────────────────────────────────────

  record ContractKey(
      String ticker,
      String assetType,
      String optionType,
      BigDecimal strikePrice,
      LocalDate expirationDate) {

    static ContractKey from(TradeEntity t) {
      return new ContractKey(
          t.getTicker(),
          t.getAssetType(),
          t.getOptionType(),
          t.getStrikePrice(),
          t.getExpirationDate());
    }

    boolean matches(TradeEntity t) {
      return Objects.equals(ticker, t.getTicker())
          && Objects.equals(assetType, t.getAssetType())
          && Objects.equals(optionType, t.getOptionType())
          && Objects.equals(strikePrice, t.getStrikePrice())
          && Objects.equals(expirationDate, t.getExpirationDate());
    }
  }

  private static class Lot {
    BigDecimal remaining;
    final BigDecimal price;
    final LocalDate date;
    final boolean isBuy;

    Lot(BigDecimal qty, BigDecimal price, LocalDate date, boolean isBuy) {
      this.remaining = qty;
      this.price = price;
      this.date = date;
      this.isBuy = isBuy;
    }
  }
}
