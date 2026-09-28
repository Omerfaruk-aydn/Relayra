package com.relayra.notification.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationPageResponse(
    List<NotificationResponse> notifications,
    long unreadCount,
    Instant beforeCreatedAt,
    UUID beforeId) {}
