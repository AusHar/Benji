package com.austinharlan.trading_dashboard.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, Long> {

  List<JournalEntryEntity> findAllByUserIdOrderByEntryDateDesc(Long userId);

  Optional<JournalEntryEntity> findByUserIdAndEntryDate(Long userId, LocalDate entryDate);

  @Query(
      "SELECT e FROM JournalEntryEntity e JOIN e.tickers t WHERE t = :ticker AND e.userId = :userId ORDER BY e.entryDate DESC")
  List<JournalEntryEntity> findByUserIdAndTicker(
      @Param("userId") Long userId, @Param("ticker") String ticker);

  @Query(
      "SELECT e FROM JournalEntryEntity e JOIN e.tags t WHERE t = :tag AND e.userId = :userId ORDER BY e.entryDate DESC")
  List<JournalEntryEntity> findByUserIdAndTag(
      @Param("userId") Long userId, @Param("tag") String tag);

  @Query(
      "SELECT e.entryDate FROM JournalEntryEntity e WHERE e.userId = :userId ORDER BY e.entryDate ASC")
  List<LocalDate> findAllEntryDatesAscByUserId(@Param("userId") Long userId);

  @Query(
      "SELECT t, COUNT(e) FROM JournalEntryEntity e JOIN e.tickers t WHERE e.userId = :userId GROUP BY t ORDER BY COUNT(e) DESC")
  List<Object[]> countByTickerAndUserId(@Param("userId") Long userId);

  @Query(
      "SELECT t, COUNT(e) FROM JournalEntryEntity e JOIN e.tags t WHERE e.userId = :userId GROUP BY t ORDER BY COUNT(e) DESC")
  List<Object[]> countByTagAndUserId(@Param("userId") Long userId);

  @Query(
      "SELECT COUNT(DISTINCT e.entryDate) FROM JournalEntryEntity e "
          + "WHERE e.userId = :userId AND e.entryDate >= :startDate AND e.entryDate < :endDate")
  long countDistinctEntryDatesInMonthByUserId(
      @Param("userId") Long userId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  @Transactional
  void deleteAllByUserId(Long userId);
}
