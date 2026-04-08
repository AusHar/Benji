package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.persistence.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoService {

  private final UserRepository userRepository;
  private final PortfolioPositionRepository portfolioRepository;
  private final TradeRepository tradeRepository;
  private final FinanceTransactionRepository financeRepository;
  private final JournalEntryRepository journalEntryRepository;
  private final JournalGoalRepository journalGoalRepository;
  private final EntityManager entityManager;

  public DemoService(
      UserRepository userRepository,
      PortfolioPositionRepository portfolioRepository,
      TradeRepository tradeRepository,
      FinanceTransactionRepository financeRepository,
      JournalEntryRepository journalEntryRepository,
      JournalGoalRepository journalGoalRepository,
      EntityManager entityManager) {
    this.userRepository = userRepository;
    this.portfolioRepository = portfolioRepository;
    this.tradeRepository = tradeRepository;
    this.financeRepository = financeRepository;
    this.journalEntryRepository = journalEntryRepository;
    this.journalGoalRepository = journalGoalRepository;
    this.entityManager = entityManager;
  }

  @Transactional
  public void resetDemoData() {
    Long demoUserId =
        userRepository
            .findByIsDemoTrue()
            .orElseThrow(() -> new IllegalStateException("Demo user not found"))
            .getId();

    // Load entries first so Hibernate cascades deletes to @ElementCollection tables
    // (journal_entry_tickers, journal_entry_tags have FK constraints)
    journalEntryRepository.deleteAll(
        journalEntryRepository.findAllByUserIdOrderByEntryDateDesc(demoUserId));
    journalGoalRepository.deleteAllByUserId(demoUserId);
    tradeRepository.deleteAllByUserId(demoUserId);
    financeRepository.deleteAllByUserId(demoUserId);
    portfolioRepository.deleteAllByUserId(demoUserId);
    entityManager.flush();
    entityManager.clear();

    seedPortfolio(demoUserId);
    seedTrades(demoUserId);
    seedFinanceTransactions(demoUserId);
    seedJournalEntries(demoUserId);
  }

  private void seedPortfolio(Long userId) {
    portfolio(userId, "NVDA", "25", "118.50");
    portfolio(userId, "OKLO", "100", "23.75");
    portfolio(userId, "MRVL", "50", "72.30");
    portfolio(userId, "AAPL", "30", "185.20");
    portfolio(userId, "MSFT", "15", "415.60");
    portfolio(userId, "AMZN", "20", "186.40");
    portfolio(userId, "LLY", "8", "790.00");
    portfolio(userId, "JPM", "25", "198.50");
    portfolio(userId, "COST", "10", "875.30");
    portfolio(userId, "PLTR", "75", "42.15");
  }

  private void portfolio(Long userId, String ticker, String qty, String basis) {
    portfolioRepository.save(
        new PortfolioPositionEntity(userId, ticker, new BigDecimal(qty), new BigDecimal(basis)));
  }

  private void seedTrades(Long userId) {
    trade(userId, "2026-01-06", "NVDA", "BUY", "25", "118.50", "Initial position");
    trade(userId, "2026-01-06", "AAPL", "BUY", "30", "185.20", "Core holding");
    trade(userId, "2026-01-08", "MRVL", "BUY", "50", "72.30", "Semiconductor play");
    trade(userId, "2026-01-10", "MSFT", "BUY", "15", "415.60", "Cloud/AI thesis");
    trade(userId, "2026-01-13", "PLTR", "BUY", "75", "42.15", "Defense/AI");
    trade(userId, "2026-01-15", "OKLO", "BUY", "100", "23.75", "Nuclear energy bet");
    trade(userId, "2026-01-22", "AMZN", "BUY", "20", "186.40", "AWS growth");
    trade(userId, "2026-01-27", "JPM", "BUY", "25", "198.50", "Financials exposure");
    trade(userId, "2026-02-03", "LLY", "BUY", "8", "790.00", "Pharma/GLP-1");
    trade(userId, "2026-02-10", "COST", "BUY", "10", "875.30", "Consumer staples");
    trade(userId, "2026-02-14", "SOFI", "BUY", "200", "11.25", "Swing trade");
    trade(userId, "2026-02-28", "SOFI", "SELL", "200", "13.80", "+$510 winner");
    trade(userId, "2026-03-03", "TSLA", "BUY", "15", "245.00", "Bounce play");
    trade(userId, "2026-03-10", "TSLA", "SELL", "15", "232.50", "-$187.50 cut loss");
    trade(userId, "2026-03-17", "AMD", "BUY", "40", "108.75", "Dip buy");
    trade(userId, "2026-03-21", "AMD", "SELL", "40", "115.20", "+$258 quick flip");

    // Options trades
    optionTrade(
        userId,
        "2026-02-10",
        "AAPL",
        "SELL",
        "1",
        "6.20",
        "Covered call against AAPL shares",
        "CALL",
        "200",
        "2026-04-18");
    optionTrade(
        userId,
        "2026-03-05",
        "AAPL",
        "BUY",
        "1",
        "3.50",
        "Bought back covered call on dip, +$270",
        "CALL",
        "200",
        "2026-04-18");
    optionTrade(
        userId,
        "2026-01-15",
        "NVDA",
        "BUY",
        "1",
        "4.80",
        "Bullish call on AI thesis",
        "CALL",
        "130",
        "2026-03-21");
    optionTrade(
        userId,
        "2026-02-20",
        "NVDA",
        "SELL",
        "1",
        "8.50",
        "Took profit on NVDA call, +$370",
        "CALL",
        "130",
        "2026-03-21");
    optionTrade(
        userId,
        "2026-02-05",
        "TSLA",
        "BUY",
        "1",
        "3.25",
        "Hedge against TSLA downturn",
        "PUT",
        "220",
        "2026-03-21");
    optionTrade(
        userId,
        "2026-03-21",
        "TSLA",
        "EXPIRE",
        "1",
        "0",
        "TSLA put expired worthless, -$325",
        "PUT",
        "220",
        "2026-03-21");
  }

  private void trade(
      Long userId,
      String date,
      String ticker,
      String side,
      String qty,
      String price,
      String notes) {
    tradeRepository.save(
        new TradeEntity(
            userId,
            ticker,
            side,
            new BigDecimal(qty),
            new BigDecimal(price),
            LocalDate.parse(date),
            notes));
  }

  private void optionTrade(
      Long userId,
      String date,
      String ticker,
      String side,
      String qty,
      String price,
      String notes,
      String optionType,
      String strike,
      String expDate) {
    tradeRepository.save(
        new TradeEntity(
            userId,
            ticker,
            side,
            new BigDecimal(qty),
            new BigDecimal(price),
            LocalDate.parse(date),
            notes,
            "OPTION",
            optionType,
            new BigDecimal(strike),
            LocalDate.parse(expDate),
            100));
  }

  private void seedFinanceTransactions(Long userId) {
    txn(userId, "2026-02-01T12:00:00Z", "Spotify Premium", "-10.99", "Subscriptions");
    txn(userId, "2026-02-01T12:00:00Z", "Netflix", "-15.49", "Subscriptions");
    txn(userId, "2026-02-03T12:00:00Z", "Whole Foods", "-87.32", "Groceries");
    txn(userId, "2026-02-05T12:00:00Z", "Shell Gas", "-52.40", "Transportation");
    txn(userId, "2026-02-07T12:00:00Z", "Chipotle", "-14.25", "Restaurants");
    txn(userId, "2026-02-08T12:00:00Z", "Amazon - Headphones", "-79.99", "Shopping");
    txn(userId, "2026-02-10T12:00:00Z", "Kroger", "-63.18", "Groceries");
    txn(userId, "2026-02-12T12:00:00Z", "Uber Eats", "-28.45", "Restaurants");
    txn(userId, "2026-02-14T12:00:00Z", "Salary Deposit", "4250.00", "Income");
    txn(userId, "2026-02-15T12:00:00Z", "Rent", "-1450.00", "Housing");
    txn(userId, "2026-02-17T12:00:00Z", "Electric Bill", "-89.50", "Utilities");
    txn(userId, "2026-02-19T12:00:00Z", "Trader Joe's", "-45.67", "Groceries");
    txn(userId, "2026-02-21T12:00:00Z", "Gym Membership", "-35.00", "Health");
    txn(userId, "2026-02-23T12:00:00Z", "Target", "-42.88", "Shopping");
    txn(userId, "2026-02-25T12:00:00Z", "Happy Hour - Brewpub", "-36.50", "Restaurants");
    txn(userId, "2026-02-28T12:00:00Z", "Salary Deposit", "4250.00", "Income");
    txn(userId, "2026-03-01T12:00:00Z", "Spotify Premium", "-10.99", "Subscriptions");
    txn(userId, "2026-03-01T12:00:00Z", "Netflix", "-15.49", "Subscriptions");
    txn(userId, "2026-03-03T12:00:00Z", "Costco", "-156.23", "Groceries");
    txn(userId, "2026-03-05T12:00:00Z", "Shell Gas", "-48.90", "Transportation");
    txn(userId, "2026-03-07T12:00:00Z", "Panda Express", "-12.75", "Restaurants");
    txn(userId, "2026-03-10T12:00:00Z", "Whole Foods", "-71.44", "Groceries");
    txn(userId, "2026-03-12T12:00:00Z", "AWS Bill", "-23.47", "Subscriptions");
    txn(userId, "2026-03-15T12:00:00Z", "Rent", "-1450.00", "Housing");
    txn(userId, "2026-03-18T12:00:00Z", "Dentist Copay", "-40.00", "Health");
  }

  private void txn(
      Long userId, String postedAt, String description, String amount, String category) {
    financeRepository.save(
        new FinanceTransactionEntity(
            userId, Instant.parse(postedAt), description, new BigDecimal(amount), category, null));
  }

  private void seedJournalEntries(Long userId) {
    journalEntry(
        userId,
        "2026-01-06",
        "Opened initial positions today. Heavy on semiconductors (NVDA, MRVL) and added nuclear exposure with OKLO. Thesis: AI capex cycle drives chip demand, nuclear provides baseload for data centers. Setting a 6-month review point.",
        new String[] {"NVDA", "MRVL", "OKLO"},
        new String[] {"thesis", "new-position"});
    journalEntry(
        userId,
        "2026-02-14",
        "Entered SOFI at $11.25 for a swing. Fintech has been beaten down but earnings were solid. Target $13-14 range, stop at $10. Small position, willing to lose it.",
        new String[] {"SOFI"},
        new String[] {"swing-trade", "entry"});
    journalEntry(
        userId,
        "2026-02-28",
        "Sold SOFI at $13.80 for +$510. Hit the target zone. Keeping the win rate positive matters more than size. Looking at TSLA for a bounce play next week.",
        new String[] {"SOFI", "TSLA"},
        new String[] {"exit", "win"});
    journalEntry(
        userId,
        "2026-03-10",
        "Cut TSLA at $232.50 for a -$187.50 loss. Bounce thesis didn't play out — macro headwinds too strong. Lesson: don't fight the trend. Moved into AMD on the semiconductor dip instead.",
        new String[] {"TSLA", "AMD"},
        new String[] {"exit", "loss", "lesson"});
  }

  private void journalEntry(
      Long userId, String date, String body, String[] tickers, String[] tags) {
    var entry =
        new JournalEntryEntity(
            userId,
            body,
            LocalDate.parse(date),
            new LinkedHashSet<>(Arrays.asList(tickers)),
            new LinkedHashSet<>(Arrays.asList(tags)));
    journalEntryRepository.save(entry);
  }
}
