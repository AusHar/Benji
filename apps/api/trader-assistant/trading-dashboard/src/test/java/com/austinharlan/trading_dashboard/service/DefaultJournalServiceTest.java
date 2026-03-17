package com.austinharlan.trading_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.austinharlan.trading_dashboard.persistence.JournalEntryEntity;
import com.austinharlan.trading_dashboard.persistence.JournalEntryRepository;
import com.austinharlan.trading_dashboard.persistence.JournalGoalEntity;
import com.austinharlan.trading_dashboard.persistence.JournalGoalRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultJournalServiceTest {

  private JournalEntryRepository entryRepo;
  private JournalGoalRepository goalRepo;
  private DefaultJournalService service;

  @BeforeEach
  void setUp() {
    entryRepo = mock(JournalEntryRepository.class);
    goalRepo = mock(JournalGoalRepository.class);
    service = new DefaultJournalService(entryRepo, goalRepo);
  }

  // ── createEntry: ticker/tag extraction ───────────────────────────────────

  @Test
  void createEntry_extractsTickersFromHtml() {
    String html = "<p><span class=\"neon-token\">$NVDA</span> and <span>$MSFT</span></p>";
    LocalDate today = LocalDate.now();
    when(entryRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

    JournalEntryEntity entry = service.createEntry(html, today);

    assertThat(entry.getTickers()).containsExactlyInAnyOrder("NVDA", "MSFT");
  }

  @Test
  void createEntry_extractsTagsFromHtml() {
    String html = "<p>Watching #macro and #growth plays today.</p>";
    when(entryRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

    JournalEntryEntity entry = service.createEntry(html, LocalDate.now());

    assertThat(entry.getTags()).containsExactlyInAnyOrder("macro", "growth");
  }

  @Test
  void createEntry_deduplicatesRepeatedTokens() {
    String html = "<p>$NVDA up, $NVDA strong, still holding $NVDA</p>";
    when(entryRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

    JournalEntryEntity entry = service.createEntry(html, LocalDate.now());

    assertThat(entry.getTickers()).containsExactlyInAnyOrder("NVDA");
  }

  @Test
  void createEntry_ignoresHtmlTagsWhenExtractingTokens() {
    // HTML tags like <strong> should not be mistaken for $TICKER patterns
    String html = "<strong>Bold text</strong> $AAPL only";
    when(entryRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

    JournalEntryEntity entry = service.createEntry(html, LocalDate.now());

    assertThat(entry.getTickers()).containsExactlyInAnyOrder("AAPL");
  }

  // ── updateEntry ───────────────────────────────────────────────────────────

  @Test
  void updateEntry_throwsWhenNotFound() {
    when(entryRepo.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.updateEntry(99L, "<p>text</p>"))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("99");
  }

  @Test
  void updateEntry_refreshesTickersAndTags() {
    JournalEntryEntity existing =
        new JournalEntryEntity("<p>$TSLA</p>", LocalDate.now(), Set.of("TSLA"), Set.of());
    when(entryRepo.findById(1L)).thenReturn(Optional.of(existing));
    when(entryRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

    JournalEntryEntity updated = service.updateEntry(1L, "<p>$NVDA #macro</p>");

    assertThat(updated.getTickers()).containsExactlyInAnyOrder("NVDA");
    assertThat(updated.getTags()).containsExactlyInAnyOrder("macro");
  }

  // ── deleteEntry ───────────────────────────────────────────────────────────

  @Test
  void deleteEntry_throwsWhenNotFound() {
    when(entryRepo.existsById(5L)).thenReturn(false);

    assertThatThrownBy(() -> service.deleteEntry(5L))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void deleteEntry_delegatesToRepository() {
    when(entryRepo.existsById(3L)).thenReturn(true);

    service.deleteEntry(3L);

    verify(entryRepo).deleteById(3L);
  }

  // ── getStats: streak ─────────────────────────────────────────────────────

  @Test
  void getStats_streakIsZeroWhenNoEntries() {
    when(entryRepo.findAllByOrderByEntryDateDesc()).thenReturn(List.of());
    when(entryRepo.countByTicker()).thenReturn(List.of());
    when(entryRepo.countByTag()).thenReturn(List.of());

    JournalService.JournalStats stats = service.getStats();

    assertThat(stats.currentStreak()).isZero();
  }

  @Test
  void getStats_streakCountsConsecutiveDaysBackFromToday() {
    LocalDate today = LocalDate.now();
    LocalDate yesterday = today.minusDays(1);
    JournalEntryEntity todayEntry =
        new JournalEntryEntity("<p>a</p>", today, Set.of(), Set.of());
    JournalEntryEntity yesterdayEntry =
        new JournalEntryEntity("<p>b</p>", yesterday, Set.of(), Set.of());
    when(entryRepo.findAllByOrderByEntryDateDesc()).thenReturn(List.of(todayEntry, yesterdayEntry));
    when(entryRepo.countByTicker()).thenReturn(List.of());
    when(entryRepo.countByTag()).thenReturn(List.of());

    JournalService.JournalStats stats = service.getStats();

    assertThat(stats.currentStreak()).isEqualTo(2);
  }

  @Test
  void getStats_streakBreaksOnGap() {
    LocalDate today = LocalDate.now();
    LocalDate twoDaysAgo = today.minusDays(2); // yesterday is missing
    JournalEntryEntity todayEntry =
        new JournalEntryEntity("<p>a</p>", today, Set.of(), Set.of());
    JournalEntryEntity oldEntry =
        new JournalEntryEntity("<p>b</p>", twoDaysAgo, Set.of(), Set.of());
    when(entryRepo.findAllByOrderByEntryDateDesc()).thenReturn(List.of(todayEntry, oldEntry));
    when(entryRepo.countByTicker()).thenReturn(List.of());
    when(entryRepo.countByTag()).thenReturn(List.of());

    JournalService.JournalStats stats = service.getStats();

    // today present, yesterday absent → walk stops after 1 day
    assertThat(stats.currentStreak()).isEqualTo(1);
  }

  // ── goal progress ─────────────────────────────────────────────────────────

  @Test
  void listGoals_milestoneProgressUsesStoredMilestoneValue() {
    JournalGoalEntity goal =
        new JournalGoalEntity(
            "Hit $500K", "milestone", BigDecimal.valueOf(500000), BigDecimal.valueOf(372000), null);
    when(goalRepo.findAll()).thenReturn(List.of(goal));
    when(entryRepo.countDistinctEntryDatesInMonth(
            org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(0L);

    JournalService.GoalWithProgress gwp = service.listGoals().getFirst();

    assertThat(gwp.currentProgress()).isEqualByComparingTo(BigDecimal.valueOf(372000));
    assertThat(gwp.progressPct()).isCloseTo(74.4, org.assertj.core.data.Offset.offset(0.1));
  }

  @Test
  void listGoals_habitProgressUsesEntryCountForCurrentMonth() {
    JournalGoalEntity goal =
        new JournalGoalEntity("Write daily", "habit", BigDecimal.valueOf(30), null, null);
    when(goalRepo.findAll()).thenReturn(List.of(goal));
    when(entryRepo.countDistinctEntryDatesInMonth(
            org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(12L);

    JournalService.GoalWithProgress gwp = service.listGoals().getFirst();

    assertThat(gwp.currentProgress()).isEqualByComparingTo(BigDecimal.valueOf(12));
    assertThat(gwp.progressPct()).isCloseTo(40.0, org.assertj.core.data.Offset.offset(0.1));
  }
}
