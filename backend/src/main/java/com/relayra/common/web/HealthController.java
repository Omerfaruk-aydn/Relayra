package com.relayra.common.web;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  @GetMapping("/api/v1/health")
  public ResponseEntity<Map<String, Object>> health() {
    return ResponseEntity.status(HttpStatus.OK)
        .body(
            Map.of(
                "status", "UP",
                "service", "relayra-backend",
                "timestamp", Instant.now().toString(),
                "requestId", UUID.randomUUID().toString()));
  }
}
