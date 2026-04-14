package com.austinharlan.trading_dashboard.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FinanceTransactionRepository
    extends JpaRepository<FinanceTransactionEntity, String> {

  List<FinanceTransactionEntity> findAllByUserIdOrderByPostedAtDesc(Long userId, Pageable pageable);

  List<FinanceTransactionEntity> findByUserIdAndCategoryIgnoreCaseOrderByPostedAtDesc(
      Long userId, String category, Pageable pageable);

  @Query(
      "SELECT t FROM FinanceTransactionEntity t WHERE t.userId = :userId AND t.postedAt >= :startInclusive AND t.postedAt < :endExclusive ORDER BY t.postedAt DESC")
  List<FinanceTransactionEntity> findWithinRangeByUserId(
      @Param("userId") Long userId,
      @Param("startInclusive") Instant startInclusive,
      @Param("endExclusive") Instant endExclusive);

  @Query(
      "select t.importDedupKey from FinanceTransactionEntity t "
          + "where t.userId = :userId and t.importDedupKey is not null")
  List<String> findImportDedupKeysByUserId(@Param("userId") Long userId);

  @Transactional
  void deleteAllByUserId(Long userId);
}
