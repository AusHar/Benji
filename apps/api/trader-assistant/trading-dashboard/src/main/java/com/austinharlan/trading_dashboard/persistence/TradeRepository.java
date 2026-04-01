package com.austinharlan.trading_dashboard.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TradeRepository extends JpaRepository<TradeEntity, Long> {

  List<TradeEntity> findAllByUserIdOrderByTradeDateDescCreatedAtDesc(Long userId);

  @Query(
      """
      SELECT t FROM TradeEntity t
      WHERE t.userId = :userId
        AND (:ticker IS NULL OR t.ticker = :ticker)
        AND (:side IS NULL OR t.side = :side)
        AND (:fromDate IS NULL OR t.tradeDate >= :fromDate)
        AND (:toDate IS NULL OR t.tradeDate <= :toDate)
      ORDER BY t.tradeDate DESC, t.createdAt DESC
      """)
  List<TradeEntity> findFilteredByUserId(
      @Param("userId") Long userId,
      @Param("ticker") String ticker,
      @Param("side") String side,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);

  @Query(
      "SELECT t FROM TradeEntity t WHERE t.userId = :userId ORDER BY t.tradeDate ASC, t.createdAt ASC")
  List<TradeEntity> findAllChronologicalByUserId(@Param("userId") Long userId);

  @Transactional
  void deleteAllByUserId(Long userId);
}
