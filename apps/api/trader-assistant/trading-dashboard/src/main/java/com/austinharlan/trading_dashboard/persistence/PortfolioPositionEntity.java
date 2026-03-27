package com.austinharlan.trading_dashboard.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(
    name = "portfolio_position",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "ticker"})})
public class PortfolioPositionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "ticker", nullable = false, length = 12)
  private String ticker;

  @Column(name = "qty", nullable = false, precision = 18, scale = 6)
  private BigDecimal qty = BigDecimal.ZERO;

  @Column(name = "basis", nullable = false, precision = 18, scale = 6)
  private BigDecimal basis = BigDecimal.ZERO;

  protected PortfolioPositionEntity() {}

  public PortfolioPositionEntity(Long userId, String ticker, BigDecimal qty, BigDecimal basis) {
    this.userId = Objects.requireNonNull(userId, "userId must not be null");
    this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
    this.qty = Objects.requireNonNull(qty, "qty must not be null");
    this.basis = Objects.requireNonNull(basis, "basis must not be null");
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

  public void setTicker(String ticker) {
    this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
  }

  public BigDecimal getQty() {
    return qty;
  }

  public void setQty(BigDecimal qty) {
    this.qty = Objects.requireNonNull(qty, "qty must not be null");
  }

  public BigDecimal getBasis() {
    return basis;
  }

  public void setBasis(BigDecimal basis) {
    this.basis = Objects.requireNonNull(basis, "basis must not be null");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PortfolioPositionEntity that)) {
      return false;
    }
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  @Override
  public String toString() {
    return "PortfolioPositionEntity{"
        + "id="
        + id
        + ", ticker='"
        + ticker
        + '\''
        + ", qty="
        + qty
        + ", basis="
        + basis
        + '}';
  }
}
