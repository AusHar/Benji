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

  @Column(name = "side", nullable = false, length = 8)
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

  @Column(name = "asset_type", nullable = false, length = 6)
  private String assetType;

  @Column(name = "option_type", length = 4)
  private String optionType;

  @Column(name = "strike_price", precision = 12, scale = 4)
  private BigDecimal strikePrice;

  @Column(name = "expiration_date")
  private LocalDate expirationDate;

  @Column(name = "multiplier", nullable = false)
  private int multiplier;

  @Column(name = "linked_trade_id")
  private Long linkedTradeId;

  @Column(name = "account", length = 20)
  private String account;

  @Column(name = "import_dedup_key", length = 64)
  private String importDedupKey;

  protected TradeEntity() {}

  /** Constructor for equity trades (backward compatible). */
  public TradeEntity(
      Long userId,
      String ticker,
      String side,
      BigDecimal quantity,
      BigDecimal pricePerShare,
      LocalDate tradeDate,
      String notes) {
    this(
        userId,
        ticker,
        side,
        quantity,
        pricePerShare,
        tradeDate,
        notes,
        "EQUITY",
        null,
        null,
        null,
        1);
  }

  /** Full constructor for option trades. */
  public TradeEntity(
      Long userId,
      String ticker,
      String side,
      BigDecimal quantity,
      BigDecimal pricePerShare,
      LocalDate tradeDate,
      String notes,
      String assetType,
      String optionType,
      BigDecimal strikePrice,
      LocalDate expirationDate,
      int multiplier) {
    this.userId = Objects.requireNonNull(userId, "userId must not be null");
    this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
    this.side = Objects.requireNonNull(side, "side must not be null");
    this.quantity = Objects.requireNonNull(quantity, "quantity must not be null");
    this.pricePerShare = Objects.requireNonNull(pricePerShare, "pricePerShare must not be null");
    this.tradeDate = Objects.requireNonNull(tradeDate, "tradeDate must not be null");
    this.notes = notes;
    this.createdAt = Instant.now();
    this.assetType = Objects.requireNonNull(assetType, "assetType must not be null");
    this.optionType = optionType;
    this.strikePrice = strikePrice;
    this.expirationDate = expirationDate;
    this.multiplier = multiplier;
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

  public String getAssetType() {
    return assetType;
  }

  public String getOptionType() {
    return optionType;
  }

  public BigDecimal getStrikePrice() {
    return strikePrice;
  }

  public LocalDate getExpirationDate() {
    return expirationDate;
  }

  public int getMultiplier() {
    return multiplier;
  }

  public Long getLinkedTradeId() {
    return linkedTradeId;
  }

  public void setLinkedTradeId(Long linkedTradeId) {
    this.linkedTradeId = linkedTradeId;
  }

  public void setQuantity(BigDecimal quantity) {
    this.quantity = quantity;
  }

  public void setPricePerShare(BigDecimal pricePerShare) {
    this.pricePerShare = pricePerShare;
  }

  public String getAccount() {
    return account;
  }

  public void setAccount(String account) {
    this.account = account;
  }

  public String getImportDedupKey() {
    return importDedupKey;
  }

  public void setImportDedupKey(String importDedupKey) {
    this.importDedupKey = importDedupKey;
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
