package com.austinharlan.trading_dashboard.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "trades")
public class TradeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "ticker", nullable = false, length = 12)
  private String ticker;

  @Column(name = "side", nullable = false, length = 4)
  private String side;

  @Column(name = "quantity", nullable = false, precision = 12, scale = 4)
  private BigDecimal quantity;

  @Column(name = "price_per_share", nullable = false, precision = 12, scale = 4)
  private BigDecimal pricePerShare;

  @Column(name = "trade_date", nullable = false)
  private LocalDate tradeDate;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected TradeEntity() {}

  public TradeEntity(
      Long userId,
      String ticker,
      String side,
      BigDecimal quantity,
      BigDecimal pricePerShare,
      LocalDate tradeDate,
      String notes) {
    this.userId = Objects.requireNonNull(userId, "userId must not be null");
    this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
    this.side = Objects.requireNonNull(side, "side must not be null");
    this.quantity = Objects.requireNonNull(quantity, "quantity must not be null");
    this.pricePerShare = Objects.requireNonNull(pricePerShare, "pricePerShare must not be null");
    this.tradeDate = Objects.requireNonNull(tradeDate, "tradeDate must not be null");
    this.notes = notes;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getTicker() {
    return ticker;
  }

  public String getSide() {
    return side;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BigDecimal getPricePerShare() {
    return pricePerShare;
  }

  public LocalDate getTradeDate() {
    return tradeDate;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TradeEntity that)) return false;
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
