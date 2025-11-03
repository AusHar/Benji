package com.austinharlan.trading_dashboard.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinanceTransactionRepository
    extends JpaRepository<FinanceTransactionEntity, String> {

  List<FinanceTransactionEntity> findAllByOrderByPostedAtDesc(Pageable pageable);

  List<FinanceTransactionEntity> findByCategoryIgnoreCaseOrderByPostedAtDesc(
      String category, Pageable pageable);

  @Query(
      """
      select transaction from FinanceTransactionEntity transaction
      where transaction.postedAt >= :startInclusive and transaction.postedAt < :endExclusive
      """)
  List<FinanceTransactionEntity> findWithinRange(
      @Param("startInclusive") java.time.Instant startInclusive,
      @Param("endExclusive") java.time.Instant endExclusive);
}
