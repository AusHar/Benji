package com.austinharlan.trading_dashboard.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "users")
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "api_key", nullable = false, unique = true, length = 64)
  private String apiKey;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(name = "is_demo", nullable = false)
  private boolean demo;

  @Column(name = "is_admin", nullable = false)
  private boolean admin;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "category_seeded", nullable = false)
  private boolean categorySeeded;

  protected UserEntity() {}

  public UserEntity(String apiKey, String displayName, boolean admin, boolean demo) {
    this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
    this.displayName = Objects.requireNonNull(displayName, "displayName");
    this.admin = admin;
    this.demo = demo;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isDemo() {
    return demo;
  }

  public boolean isAdmin() {
    return admin;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public boolean isCategorySeeded() {
    return categorySeeded;
  }

  public void setCategorySeeded(boolean categorySeeded) {
    this.categorySeeded = categorySeeded;
  }
}
