package com.austinharlan.trading_dashboard.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<TradeEntity, Long> {

  List<TradeEntity> findAllByOrderByTradeDateDescCreatedAtDesc();

  @Query(
      """
      select t from TradeEntity t
      where (:ticker is null or t.ticker = :ticker)
        and (:side is null or t.side = :side)
        and (:fromDate is null or t.tradeDate >= :fromDate)
        and (:toDate is null or t.tradeDate <= :toDate)
      order by t.tradeDate desc, t.createdAt desc
      """)
  List<TradeEntity> findFiltered(
      @Param("ticker") String ticker,
      @Param("side") String side,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);

  @Query(
      """
      select t from TradeEntity t
      order by t.tradeDate asc, t.createdAt asc
      """)
  List<TradeEntity> findAllChronological();
}
