package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.service.DailyImageService;
import com.austinharlan.trading_dashboard.service.DailyImageService.DailyImage;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DailyImageController {
  private final DailyImageService dailyImageService;

  public DailyImageController(DailyImageService dailyImageService) {
    this.dailyImageService = dailyImageService;
  }

  @GetMapping("/api/daily-image")
  public ResponseEntity<?> getDailyImage() {
    DailyImage image = dailyImageService.getImageOfTheDay();
    if (image == null) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.ok(
        Map.of("url", image.url(), "title", image.title(), "author", image.author()));
  }
}
