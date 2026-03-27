package com.austinharlan.trading_dashboard.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "journal_entries")
public class JournalEntryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "body", nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "entry_date", nullable = false)
  private LocalDate entryDate;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "journal_entry_tickers", joinColumns = @JoinColumn(name = "entry_id"))
  @Column(name = "ticker")
  private Set<String> tickers = new HashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "journal_entry_tags", joinColumns = @JoinColumn(name = "entry_id"))
  @Column(name = "tag")
  private Set<String> tags = new HashSet<>();

  protected JournalEntryEntity() {}

  public JournalEntryEntity(
      Long userId, String body, LocalDate entryDate, Set<String> tickers, Set<String> tags) {
    this.userId = Objects.requireNonNull(userId, "userId must not be null");
    this.body = Objects.requireNonNull(body, "body must not be null");
    this.entryDate = Objects.requireNonNull(entryDate, "entryDate must not be null");
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
    this.tickers = tickers != null ? new HashSet<>(tickers) : new HashSet<>();
    this.tags = tags != null ? new HashSet<>(tags) : new HashSet<>();
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = Objects.requireNonNull(body);
  }

  public LocalDate getEntryDate() {
    return entryDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = Objects.requireNonNull(updatedAt);
  }

  public Set<String> getTickers() {
    return Collections.unmodifiableSet(tickers);
  }

  public void setTickers(Set<String> tickers) {
    this.tickers = tickers != null ? new HashSet<>(tickers) : new HashSet<>();
  }

  public Set<String> getTags() {
    return Collections.unmodifiableSet(tags);
  }

  public void setTags(Set<String> tags) {
    this.tags = tags != null ? new HashSet<>(tags) : new HashSet<>();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JournalEntryEntity that)) return false;
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
