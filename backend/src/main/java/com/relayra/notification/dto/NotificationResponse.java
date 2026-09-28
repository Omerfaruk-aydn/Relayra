package com.relayra.notification.dto;

import com.relayra.notification.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    NotificationType type,
    UUID actorId,
    UUID communityId,
    UUID channelId,
    UUID conversationId,
    UUID messageId,
    UUID referenceId,
    String detail,
    Instant readAt,
    Instant createdAt) {}
