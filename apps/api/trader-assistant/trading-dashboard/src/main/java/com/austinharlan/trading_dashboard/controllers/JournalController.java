package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.persistence.JournalEntryEntity;
import com.austinharlan.trading_dashboard.persistence.JournalGoalEntity;
import com.austinharlan.trading_dashboard.service.JournalService;
import com.austinharlan.trading_dashboard.service.JournalService.GoalWithProgress;
import com.austinharlan.trading_dashboard.service.JournalService.JournalStats;
import com.austinharlan.tradingdashboard.api.JournalApi;
import com.austinharlan.tradingdashboard.dto.JournalEntryRequest;
import com.austinharlan.tradingdashboard.dto.JournalEntryResponse;
import com.austinharlan.tradingdashboard.dto.JournalGoalRequest;
import com.austinharlan.tradingdashboard.dto.JournalGoalResponse;
import com.austinharlan.tradingdashboard.dto.JournalStatsResponse;
import com.austinharlan.tradingdashboard.dto.JournalTokenCount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JournalController implements JournalApi {

  private final JournalService journalService;

  public JournalController(JournalService journalService) {
    this.journalService = journalService;
  }

  @Override
  public ResponseEntity<List<JournalEntryResponse>> listJournalEntries(
      String ticker, String tag, LocalDate entryDate) {
    List<JournalEntryEntity> entries = journalService.listEntries(ticker, tag, entryDate);
    return ResponseEntity.ok(entries.stream().map(this::toEntryResponse).toList());
  }

  @Override
  public ResponseEntity<JournalEntryResponse> createJournalEntry(
      JournalEntryRequest journalEntryRequest) {
    JournalEntryEntity entry =
        journalService.createEntry(
            journalEntryRequest.getBody(), journalEntryRequest.getEntryDate());
    return ResponseEntity.status(201).body(toEntryResponse(entry));
  }

  @Override
  public ResponseEntity<JournalEntryResponse> updateJournalEntry(
      Long id, JournalEntryRequest journalEntryRequest) {
    JournalEntryEntity entry = journalService.updateEntry(id, journalEntryRequest.getBody());
    return ResponseEntity.ok(toEntryResponse(entry));
  }

  @Override
  public ResponseEntity<Void> deleteJournalEntry(Long id) {
    journalService.deleteEntry(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<List<JournalGoalResponse>> listJournalGoals() {
    return ResponseEntity.ok(
        journalService.listGoals().stream().map(this::toGoalResponse).toList());
  }

  @Override
  public ResponseEntity<JournalGoalResponse> createJournalGoal(
      JournalGoalRequest journalGoalRequest) {
    JournalGoalEntity goal =
        journalService.createGoal(
            journalGoalRequest.getLabel(),
            journalGoalRequest.getGoalType().getValue(),
            toDecimal(journalGoalRequest.getTargetValue()),
            toDecimal(journalGoalRequest.getMilestoneValue()),
            journalGoalRequest.getDeadline());
    // re-fetch with progress for the response
    GoalWithProgress gwp =
        journalService.listGoals().stream()
            .filter(g -> g.goal().getId().equals(goal.getId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new jakarta.persistence.EntityNotFoundException("Goal not found after create"));
    return ResponseEntity.status(201).body(toGoalResponse(gwp));
  }

  @Override
  public ResponseEntity<JournalGoalResponse> updateJournalGoal(
      Long id, JournalGoalRequest journalGoalRequest) {
    journalService.updateGoal(
        id,
        journalGoalRequest.getLabel(),
        toDecimal(journalGoalRequest.getTargetValue()),
        toDecimal(journalGoalRequest.getMilestoneValue()),
        journalGoalRequest.getDeadline());
    GoalWithProgress gwp =
        journalService.listGoals().stream()
            .filter(g -> g.goal().getId().equals(id))
            .findFirst()
            .orElseThrow(
                () -> new jakarta.persistence.EntityNotFoundException("Goal " + id + " not found"));
    return ResponseEntity.ok(toGoalResponse(gwp));
  }

  @Override
  public ResponseEntity<Void> deleteJournalGoal(Long id) {
    journalService.deleteGoal(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<JournalStatsResponse> getJournalStats() {
    JournalStats stats = journalService.getStats();
    Map<String, Integer> calendarMap =
        stats.calendar().entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
    List<JournalTokenCount> tokenCounts =
        stats.mostMentioned().stream()
            .map(tc -> new JournalTokenCount().token(tc.token()).count(tc.count()))
            .toList();
    JournalStatsResponse response =
        new JournalStatsResponse()
            .entryCount((long) stats.entryCount())
            .currentStreak(stats.currentStreak())
            .calendar(calendarMap)
            .mostMentioned(tokenCounts);
    return ResponseEntity.ok(response);
  }

  // ── private mappers ───────────────────────────────────────────────────────

  private JournalEntryResponse toEntryResponse(JournalEntryEntity e) {
    return new JournalEntryResponse()
        .id(e.getId())
        .body(e.getBody())
        .entryDate(e.getEntryDate())
        .tickers(List.copyOf(e.getTickers()))
        .tags(List.copyOf(e.getTags()))
        .createdAt(OffsetDateTime.ofInstant(e.getCreatedAt(), ZoneOffset.UTC))
        .updatedAt(OffsetDateTime.ofInstant(e.getUpdatedAt(), ZoneOffset.UTC));
  }

  private JournalGoalResponse toGoalResponse(GoalWithProgress gwp) {
    JournalGoalEntity g = gwp.goal();
    return new JournalGoalResponse()
        .id(g.getId())
        .label(g.getLabel())
        .goalType(JournalGoalResponse.GoalTypeEnum.fromValue(g.getGoalType()))
        .targetValue(g.getTargetValue() != null ? g.getTargetValue().doubleValue() : null)
        .currentProgress(gwp.currentProgress() != null ? gwp.currentProgress().doubleValue() : null)
        .progressPct(gwp.progressPct())
        .deadline(g.getDeadline())
        .createdAt(OffsetDateTime.ofInstant(g.getCreatedAt(), ZoneOffset.UTC));
  }

  private static BigDecimal toDecimal(Double value) {
    return value != null ? BigDecimal.valueOf(value) : null;
  }
}
