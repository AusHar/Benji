package com.austinharlan.trading_dashboard.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, Long> {

  List<JournalEntryEntity> findAllByOrderByEntryDateDesc();

  Optional<JournalEntryEntity> findByEntryDate(LocalDate entryDate);

  @Query(
      """
      select distinct e from JournalEntryEntity e
      join e.tickers t
      where t = :ticker
      order by e.entryDate desc
      """)
  List<JournalEntryEntity> findByTicker(@Param("ticker") String ticker);

  @Query(
      """
      select distinct e from JournalEntryEntity e
      join e.tags t
      where t = :tag
      order by e.entryDate desc
      """)
  List<JournalEntryEntity> findByTag(@Param("tag") String tag);

  /** All entry dates ascending — used for streak calculation. */
  @Query("select e.entryDate from JournalEntryEntity e order by e.entryDate asc")
  List<LocalDate> findAllEntryDatesAsc();

  /** Ticker -> count of entries mentioning it. */
  @Query(
      """
      select t, count(e) from JournalEntryEntity e
      join e.tickers t
      group by t
      order by count(e) desc
      """)
  List<Object[]> countByTicker();

  /** Tag -> count of entries mentioning it. */
  @Query(
      """
      select t, count(e) from JournalEntryEntity e
      join e.tags t
      group by t
      order by count(e) desc
      """)
  List<Object[]> countByTag();

  /**
   * Count of distinct entry_date values in a given calendar month. Used for habit goal progress.
   */
  @Query(
      """
      select count(distinct e.entryDate) from JournalEntryEntity e
      where year(e.entryDate) = :year and month(e.entryDate) = :month
      """)
  long countDistinctEntryDatesInMonth(@Param("year") int year, @Param("month") int month);
}
