package com.austinharlan.trading_dashboard.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "journal_goals")
public class JournalGoalEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "label", nullable = false)
  private String label;

  @Column(name = "goal_type", nullable = false)
  private String goalType;

  @Column(name = "target_value")
  private BigDecimal targetValue;

  @Column(name = "milestone_value")
  private BigDecimal milestoneValue;

  @Column(name = "deadline")
  private LocalDate deadline;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected JournalGoalEntity() {}

  public JournalGoalEntity(
      String label,
      String goalType,
      BigDecimal targetValue,
      BigDecimal milestoneValue,
      LocalDate deadline) {
    this.label = Objects.requireNonNull(label, "label must not be null");
    this.goalType = Objects.requireNonNull(goalType, "goalType must not be null");
    this.targetValue = targetValue;
    this.milestoneValue = milestoneValue;
    this.deadline = deadline;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = Objects.requireNonNull(label);
  }

  public String getGoalType() {
    return goalType;
  }

  public BigDecimal getTargetValue() {
    return targetValue;
  }

  public void setTargetValue(BigDecimal targetValue) {
    this.targetValue = targetValue;
  }

  public BigDecimal getMilestoneValue() {
    return milestoneValue;
  }

  public void setMilestoneValue(BigDecimal milestoneValue) {
    this.milestoneValue = milestoneValue;
  }

  public LocalDate getDeadline() {
    return deadline;
  }

  public void setDeadline(LocalDate deadline) {
    this.deadline = deadline;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JournalGoalEntity that)) return false;
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
