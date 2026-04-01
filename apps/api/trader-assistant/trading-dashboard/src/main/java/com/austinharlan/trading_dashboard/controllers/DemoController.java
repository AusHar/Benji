package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.service.DemoService;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

  private static final long COOLDOWN_MS = 5_000;
  private final AtomicLong lastReset = new AtomicLong(0);

  private final DemoService demoService;

  public DemoController(DemoService demoService) {
    this.demoService = demoService;
  }

  @PostMapping("/api/demo/session")
  public ResponseEntity<Map<String, String>> startDemoSession() {
    long now = System.currentTimeMillis();
    long prev = lastReset.get();
    if (now - prev < COOLDOWN_MS || !lastReset.compareAndSet(prev, now)) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body(Map.of("error", "Please wait a few seconds before retrying"));
    }
    demoService.resetDemoData();
    return ResponseEntity.ok(Map.of("apiKey", "demo"));
  }
}
