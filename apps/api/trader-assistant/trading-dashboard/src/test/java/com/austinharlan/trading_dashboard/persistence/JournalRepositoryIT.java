package com.austinharlan.trading_dashboard.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
class JournalRepositoryIT extends DatabaseIntegrationTest {

  @Autowired private JournalEntryRepository entryRepo;
  @Autowired private JournalGoalRepository goalRepo;
  @Autowired private UserRepository userRepository;

  private Long testUserId;

  @BeforeEach
  void setUp() {
    testUserId = userRepository.findByApiKey("test-api-key").orElseThrow().getId();
  }

  @AfterEach
  void cleanup() {
    entryRepo.deleteAll();
    goalRepo.deleteAll();
  }

  @Test
  void flywayCreatesJournalTables() {
    assertThat(entryRepo.findAll()).isEmpty();
    assertThat(goalRepo.findAll()).isEmpty();
  }

  @Test
  void saveAndFindEntryByDate() {
    LocalDate target = LocalDate.of(2026, 3, 15);
    JournalEntryEntity entry =
        new JournalEntryEntity(testUserId, "<p>$NVDA</p>", target, Set.of("NVDA"), Set.of("macro"));
    entryRepo.save(entry);

    Optional<JournalEntryEntity> found = entryRepo.findByUserIdAndEntryDate(testUserId, target);

    assertThat(found).isPresent();
    assertThat(found.get().getTickers()).containsExactlyInAnyOrder("NVDA");
    assertThat(found.get().getTags()).containsExactlyInAnyOrder("macro");
  }

  @Test
  void findByTicker_returnsOnlyMatchingEntries() {
    entryRepo.save(
        new JournalEntryEntity(
            testUserId, "<p>$NVDA</p>", LocalDate.of(2026, 3, 1), Set.of("NVDA"), Set.of()));
    entryRepo.save(
        new JournalEntryEntity(
            testUserId, "<p>$MSFT</p>", LocalDate.of(2026, 3, 2), Set.of("MSFT"), Set.of()));

    List<JournalEntryEntity> results = entryRepo.findByUserIdAndTicker(testUserId, "NVDA");

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getTickers()).contains("NVDA");
  }

  @Test
  void findByTag_returnsOnlyMatchingEntries() {
    entryRepo.save(
        new JournalEntryEntity(
            testUserId, "<p>#macro</p>", LocalDate.of(2026, 3, 1), Set.of(), Set.of("macro")));
    entryRepo.save(
        new JournalEntryEntity(
            testUserId, "<p>#growth</p>", LocalDate.of(2026, 3, 2), Set.of(), Set.of("growth")));

    List<JournalEntryEntity> results = entryRepo.findByUserIdAndTag(testUserId, "macro");

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getTags()).contains("macro");
  }

  @Test
  void deletingEntry_cascadesToTickersAndTags() {
    JournalEntryEntity entry =
        entryRepo.save(
            new JournalEntryEntity(
                testUserId,
                "<p>$TSLA #ev</p>",
                LocalDate.of(2026, 3, 3),
                Set.of("TSLA"),
                Set.of("ev")));

    entryRepo.deleteById(entry.getId());

    // If cascade works, the entry is gone; the child rows are removed automatically.
    assertThat(entryRepo.findById(entry.getId())).isEmpty();
  }

  @Test
  void countDistinctEntryDatesInMonth() {
    entryRepo.save(
        new JournalEntryEntity(
            testUserId, "<p>a</p>", LocalDate.of(2026, 3, 1), Set.of(), Set.of()));
    entryRepo.save(
        new JournalEntryEntity(
            testUserId, "<p>b</p>", LocalDate.of(2026, 3, 5), Set.of(), Set.of()));
    entryRepo.save(
        new JournalEntryEntity(
            testUserId, "<p>c</p>", LocalDate.of(2026, 4, 1), Set.of(), Set.of()));

    long marchCount = entryRepo.countDistinctEntryDatesInMonthByUserId(testUserId, 2026, 3);

    assertThat(marchCount).isEqualTo(2);
  }

  @Test
  void uniqueConstraint_preventsSecondEntryForSameDate() {
    entryRepo.save(
        new JournalEntryEntity(
            testUserId, "<p>first</p>", LocalDate.of(2026, 3, 10), Set.of(), Set.of()));

    JournalEntryEntity duplicate =
        new JournalEntryEntity(
            testUserId, "<p>second</p>", LocalDate.of(2026, 3, 10), Set.of(), Set.of());

    assertThatThrownBy(() -> entryRepo.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
