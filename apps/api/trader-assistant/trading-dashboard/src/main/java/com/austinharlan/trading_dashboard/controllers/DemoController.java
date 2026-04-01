package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.service.DemoService;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

  private final long cooldownMs;
  private final AtomicLong lastReset = new AtomicLong(0);

  private final DemoService demoService;

  public DemoController(
      DemoService demoService, @Value("${demo.cooldown-ms:5000}") long cooldownMs) {
    this.demoService = demoService;
    this.cooldownMs = cooldownMs;
  }

  @PostMapping("/api/demo/session")
  public ResponseEntity<Map<String, String>> startDemoSession() {
    long now = System.currentTimeMillis();
    long prev = lastReset.get();
    if (now - prev < cooldownMs || !lastReset.compareAndSet(prev, now)) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body(Map.of("error", "Please wait a few seconds before retrying"));
    }
    demoService.resetDemoData();
    return ResponseEntity.ok(Map.of("apiKey", "demo"));
  }
}
