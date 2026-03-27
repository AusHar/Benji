package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.config.UserContext;
import com.austinharlan.trading_dashboard.persistence.JournalEntryEntity;
import com.austinharlan.trading_dashboard.persistence.JournalEntryRepository;
import com.austinharlan.trading_dashboard.persistence.JournalGoalEntity;
import com.austinharlan.trading_dashboard.persistence.JournalGoalRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultJournalService implements JournalService {

  private static final Pattern TICKER_PATTERN = Pattern.compile("\\$([A-Z]{1,10})");
  private static final Pattern TAG_PATTERN = Pattern.compile("#([a-zA-Z][a-zA-Z0-9_]*)");

  private final JournalEntryRepository entryRepository;
  private final JournalGoalRepository goalRepository;

  public DefaultJournalService(
      JournalEntryRepository entryRepository, JournalGoalRepository goalRepository) {
    this.entryRepository = entryRepository;
    this.goalRepository = goalRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<JournalEntryEntity> listEntries(
      @Nullable String ticker, @Nullable String tag, @Nullable LocalDate entryDate) {
    long userId = UserContext.current().userId();
    if (entryDate != null) {
      return entryRepository
          .findByUserIdAndEntryDate(userId, entryDate)
          .map(List::of)
          .orElse(List.of());
    }
    if (ticker != null) {
      return entryRepository.findByUserIdAndTicker(userId, ticker.toUpperCase());
    }
    if (tag != null) {
      return entryRepository.findByUserIdAndTag(userId, tag.toLowerCase());
    }
    return entryRepository.findAllByUserIdOrderByEntryDateDesc(userId);
  }

  @Override
  public JournalEntryEntity createEntry(String body, LocalDate entryDate) {
    long userId = UserContext.current().userId();
    Set<String> tickers = extractTickers(body);
    Set<String> tags = extractTags(body);
    JournalEntryEntity entry = new JournalEntryEntity(userId, body, entryDate, tickers, tags);
    return entryRepository.save(entry);
  }

  @Override
  public JournalEntryEntity updateEntry(long id, String body) {
    long userId = UserContext.current().userId();
    JournalEntryEntity entry =
        entryRepository.findById(id).orElseThrow(() -> notFound("Entry", id));
    if (!entry.getUserId().equals(userId)) throw notFound("Entry", id);
    entry.setBody(body);
    entry.setTickers(extractTickers(body));
    entry.setTags(extractTags(body));
    entry.setUpdatedAt(Instant.now());
    return entryRepository.save(entry);
  }

  @Override
  public void deleteEntry(long id) {
    long userId = UserContext.current().userId();
    JournalEntryEntity entry =
        entryRepository.findById(id).orElseThrow(() -> notFound("Entry", id));
    if (!entry.getUserId().equals(userId)) throw notFound("Entry", id);
    entryRepository.deleteById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public List<GoalWithProgress> listGoals() {
    long userId = UserContext.current().userId();
    YearMonth currentMonth = YearMonth.now();
    long habitProgress =
        entryRepository.countDistinctEntryDatesInMonthByUserId(
            userId, currentMonth.getYear(), currentMonth.getMonthValue());

    return goalRepository.findAllByUserId(userId).stream()
        .map(goal -> toGoalWithProgress(goal, habitProgress))
        .toList();
  }

  @Override
  public JournalGoalEntity createGoal(
      String label,
      String goalType,
      @Nullable BigDecimal targetValue,
      @Nullable BigDecimal milestoneValue,
      @Nullable LocalDate deadline) {
    long userId = UserContext.current().userId();
    JournalGoalEntity goal =
        new JournalGoalEntity(userId, label, goalType, targetValue, milestoneValue, deadline);
    return goalRepository.save(goal);
  }

  @Override
  public JournalGoalEntity updateGoal(
      long id,
      String label,
      @Nullable BigDecimal targetValue,
      @Nullable BigDecimal milestoneValue,
      @Nullable LocalDate deadline) {
    long userId = UserContext.current().userId();
    JournalGoalEntity goal = goalRepository.findById(id).orElseThrow(() -> notFound("Goal", id));
    if (!goal.getUserId().equals(userId)) throw notFound("Goal", id);
    goal.setLabel(label);
    goal.setTargetValue(targetValue);
    goal.setMilestoneValue(milestoneValue);
    goal.setDeadline(deadline);
    return goalRepository.save(goal);
  }

  @Override
  public void deleteGoal(long id) {
    long userId = UserContext.current().userId();
    JournalGoalEntity goal = goalRepository.findById(id).orElseThrow(() -> notFound("Goal", id));
    if (!goal.getUserId().equals(userId)) throw notFound("Goal", id);
    goalRepository.deleteById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public JournalStats getStats() {
    long userId = UserContext.current().userId();
    List<JournalEntryEntity> allEntries =
        entryRepository.findAllByUserIdOrderByEntryDateDesc(userId);
    long entryCount = allEntries.size();
    List<LocalDate> datesAsc = entryRepository.findAllEntryDatesAscByUserId(userId);
    int streak = computeStreak(datesAsc);
    Map<LocalDate, Integer> calendar = buildCalendar(allEntries);
    List<JournalStats.TokenCount> mostMentioned = buildMostMentioned(userId);
    return new JournalStats(entryCount, streak, calendar, mostMentioned);
  }

  // ── private helpers ──────────────────────────────────────────────────────

  private static Set<String> extractTickers(String html) {
    String text = html.replaceAll("<[^>]+>", " ");
    Matcher m = TICKER_PATTERN.matcher(text);
    Set<String> result = new LinkedHashSet<>();
    while (m.find()) result.add(m.group(1));
    return result;
  }

  private static Set<String> extractTags(String html) {
    String text = html.replaceAll("<[^>]+>", " ");
    Matcher m = TAG_PATTERN.matcher(text);
    Set<String> result = new LinkedHashSet<>();
    while (m.find()) result.add(m.group(1).toLowerCase());
    return result;
  }

  private static int computeStreak(List<LocalDate> datesAsc) {
    if (datesAsc.isEmpty()) return 0;
    Set<LocalDate> dateSet = new HashSet<>(datesAsc);
    int streak = 0;
    LocalDate check = LocalDate.now();
    while (dateSet.contains(check)) {
      streak++;
      check = check.minusDays(1);
    }
    return streak;
  }

  private static Map<LocalDate, Integer> buildCalendar(List<JournalEntryEntity> entries) {
    Map<LocalDate, Integer> calendar = new LinkedHashMap<>();
    // Populate the current month with all days at level 0
    YearMonth month = YearMonth.now();
    for (int day = 1; day <= month.lengthOfMonth(); day++) {
      calendar.put(month.atDay(day), 0);
    }
    // Override days that have entries
    for (JournalEntryEntity e : entries) {
      if (calendar.containsKey(e.getEntryDate())) {
        int level = isRichEntry(e) ? 2 : 1;
        calendar.put(e.getEntryDate(), level);
      }
    }
    return calendar;
  }

  /**
   * Activity level 2: entry contains an img/blockquote (media embed) OR 3+ tickers+tags combined.
   */
  private static boolean isRichEntry(JournalEntryEntity e) {
    boolean hasMedia = e.getBody().contains("<img") || e.getBody().contains("<blockquote");
    boolean hasThreePlusTags = (e.getTickers().size() + e.getTags().size()) >= 3;
    return hasMedia || hasThreePlusTags;
  }

  private List<JournalStats.TokenCount> buildMostMentioned(long userId) {
    List<JournalStats.TokenCount> counts = new ArrayList<>();
    entryRepository
        .countByTickerAndUserId(userId)
        .forEach(
            row ->
                counts.add(
                    new JournalStats.TokenCount("$" + row[0], ((Number) row[1]).intValue())));
    entryRepository
        .countByTagAndUserId(userId)
        .forEach(
            row ->
                counts.add(
                    new JournalStats.TokenCount("#" + row[0], ((Number) row[1]).intValue())));
    counts.sort((a, b) -> Integer.compare(b.count(), a.count()));
    return counts.stream().limit(10).toList();
  }

  private static GoalWithProgress toGoalWithProgress(JournalGoalEntity goal, long habitProgress) {
    BigDecimal progress;
    if ("habit".equals(goal.getGoalType())) {
      progress = BigDecimal.valueOf(habitProgress);
    } else {
      progress = goal.getMilestoneValue();
    }
    Double progressPct = null;
    if (progress != null
        && goal.getTargetValue() != null
        && goal.getTargetValue().compareTo(BigDecimal.ZERO) > 0) {
      progressPct =
          progress
              .divide(goal.getTargetValue(), 4, RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(100))
              .doubleValue();
    }
    return new GoalWithProgress(goal, progress, progressPct);
  }

  private static EntityNotFoundException notFound(String type, long id) {
    return new EntityNotFoundException(type + " not found: " + id);
  }
}
