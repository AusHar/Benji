package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.persistence.JournalEntryEntity;
import com.austinharlan.trading_dashboard.persistence.JournalGoalEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

public interface JournalService {

  List<JournalEntryEntity> listEntries(
      @Nullable String ticker, @Nullable String tag, @Nullable LocalDate entryDate);

  JournalEntryEntity createEntry(String body, LocalDate entryDate);

  JournalEntryEntity updateEntry(long id, String body);

  void deleteEntry(long id);

  List<GoalWithProgress> listGoals();

  JournalGoalEntity createGoal(
      String label,
      String goalType,
      @Nullable BigDecimal targetValue,
      @Nullable BigDecimal milestoneValue,
      @Nullable LocalDate deadline);

  JournalGoalEntity updateGoal(
      long id,
      String label,
      @Nullable BigDecimal targetValue,
      @Nullable BigDecimal milestoneValue,
      @Nullable LocalDate deadline);

  void deleteGoal(long id);

  JournalStats getStats();

  /** Goal entity paired with its computed progress. */
  record GoalWithProgress(
      JournalGoalEntity goal, BigDecimal currentProgress, @Nullable Double progressPct) {}

  /** Stats response data. */
  record JournalStats(
      long entryCount,
      int currentStreak,
      Map<LocalDate, Integer> calendar,
      List<TokenCount> mostMentioned) {
    public record TokenCount(String token, int count) {}
  }
}
