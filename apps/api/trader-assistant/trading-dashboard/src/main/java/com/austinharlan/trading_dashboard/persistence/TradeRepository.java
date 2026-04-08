package com.austinharlan.trading_dashboard.persistence;

import jakarta.transaction.Transactional;
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
      select t from TradeEntity t
      where t.userId = :userId
        and (:ticker is null or t.ticker = :ticker)
        and (:side is null or t.side = :side)
        and (:fromDate is null or t.tradeDate >= :fromDate)
        and (:toDate is null or t.tradeDate <= :toDate)
      order by t.tradeDate desc, t.createdAt desc
      """)
  List<TradeEntity> findFilteredByUserId(
      @Param("userId") Long userId,
      @Param("ticker") String ticker,
      @Param("side") String side,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);

  @Query(
      """
      select t from TradeEntity t
      where t.userId = :userId
      order by t.tradeDate asc, t.createdAt asc
      """)
  List<TradeEntity> findAllChronologicalByUserId(@Param("userId") Long userId);

  @Transactional
  void deleteAllByUserId(Long userId);
}
