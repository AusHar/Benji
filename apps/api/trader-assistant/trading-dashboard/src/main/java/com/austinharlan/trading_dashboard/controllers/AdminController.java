package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.config.UserContext;
import com.austinharlan.trading_dashboard.persistence.UserEntity;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

  private final UserRepository userRepository;

  public AdminController(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @PostMapping("/api/admin/users")
  public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, String> body) {
    UserContext ctx = UserContext.current();
    if (!ctx.isAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    String displayName = body.get("displayName");
    if (displayName == null || displayName.isBlank()) {
      return ResponseEntity.badRequest().build();
    }

    String apiKey = UUID.randomUUID().toString();
    UserEntity user = userRepository.save(new UserEntity(apiKey, displayName, false, false));

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            Map.of(
                "id", user.getId(),
                "displayName", user.getDisplayName(),
                "apiKey", user.getApiKey()));
  }
}
