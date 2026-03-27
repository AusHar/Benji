package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.service.DemoService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

  private final DemoService demoService;

  public DemoController(DemoService demoService) {
    this.demoService = demoService;
  }

  @PostMapping("/api/demo/session")
  public ResponseEntity<Map<String, String>> startDemoSession() {
    demoService.resetDemoData();
    return ResponseEntity.ok(Map.of("apiKey", "demo"));
  }
}
