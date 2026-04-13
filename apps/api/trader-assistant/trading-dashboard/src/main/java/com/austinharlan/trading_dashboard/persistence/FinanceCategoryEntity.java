package com.austinharlan.trading_dashboard.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "finance_category")
public class FinanceCategoryEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false, length = 36)
  private String id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "slug", nullable = false, length = 64)
  private String slug;

  @Column(name = "label", nullable = false, length = 64)
  private String label;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected FinanceCategoryEntity() {}

  public FinanceCategoryEntity(Long userId, String slug, String label, int sortOrder) {
    this.id = UUID.randomUUID().toString();
    this.userId = Objects.requireNonNull(userId, "userId");
    this.slug = Objects.requireNonNull(slug, "slug");
    this.label = Objects.requireNonNull(label, "label");
    this.sortOrder = sortOrder;
    this.createdAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getSlug() {
    return slug;
  }

  public String getLabel() {
    return label;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
