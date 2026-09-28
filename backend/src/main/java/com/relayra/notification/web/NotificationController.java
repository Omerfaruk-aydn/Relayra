package com.relayra.notification.web;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.notification.NotificationService;
import com.relayra.notification.dto.NotificationPageResponse;
import com.relayra.notification.dto.NotificationResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationService notifications;

  public NotificationController(NotificationService notifications) {
    this.notifications = notifications;
  }

  @GetMapping
  public ResponseEntity<NotificationPageResponse> list(
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Instant beforeCreatedAt,
      @RequestParam(required = false) UUID beforeId,
      Authentication authentication) {
    return ResponseEntity.ok(
        notifications.list(requireCaller(authentication), limit, beforeCreatedAt, beforeId));
  }

  @PatchMapping("/{notificationId}/read")
  public ResponseEntity<NotificationResponse> markRead(
      @PathVariable UUID notificationId, Authentication authentication) {
    return ResponseEntity.ok(notifications.markRead(requireCaller(authentication), notificationId));
  }

  @PostMapping("/read-all")
  public ResponseEntity<Void> markAllRead(Authentication authentication) {
    notifications.markAllRead(requireCaller(authentication));
    return ResponseEntity.noContent().build();
  }

  private UUID requireCaller(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
    return userId;
  }
}
