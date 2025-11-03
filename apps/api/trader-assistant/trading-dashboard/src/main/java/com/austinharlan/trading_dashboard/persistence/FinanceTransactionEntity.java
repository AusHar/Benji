package com.austinharlan.trading_dashboard.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "finance_transaction")
public class FinanceTransactionEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false, length = 36)
  private String id;

  @Column(name = "posted_at", nullable = false)
  private Instant postedAt;

  @Column(name = "description", nullable = false, length = 255)
  private String description;

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal amount = BigDecimal.ZERO;

  @Column(name = "category", length = 64)
  private String category;

  @Column(name = "notes")
  private String notes;

  protected FinanceTransactionEntity() {
    // for JPA
  }

  public FinanceTransactionEntity(
      Instant postedAt,
      String description,
      BigDecimal amount,
      String category,
      String notes) {
    this(UUID.randomUUID().toString(), postedAt, description, amount, category, notes);
  }

  public FinanceTransactionEntity(
      String id,
      Instant postedAt,
      String description,
      BigDecimal amount,
      String category,
      String notes) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.postedAt = Objects.requireNonNull(postedAt, "postedAt must not be null");
    this.description = Objects.requireNonNull(description, "description must not be null");
    this.amount = Objects.requireNonNull(amount, "amount must not be null");
    this.category = category;
    this.notes = notes;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = Objects.requireNonNull(id, "id must not be null");
  }

  public Instant getPostedAt() {
    return postedAt;
  }

  public void setPostedAt(Instant postedAt) {
    this.postedAt = Objects.requireNonNull(postedAt, "postedAt must not be null");
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = Objects.requireNonNull(description, "description must not be null");
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = Objects.requireNonNull(amount, "amount must not be null");
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FinanceTransactionEntity that)) {
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
    return "FinanceTransactionEntity{"
        + "id='"
        + id
        + '\''
        + ", postedAt="
        + postedAt
        + ", description='"
        + description
        + '\''
        + ", amount="
        + amount
        + ", category='"
        + category
        + '\''
        + ", notes='"
        + notes
        + '\''
        + '}';
  }
}
