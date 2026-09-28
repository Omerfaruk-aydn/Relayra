package com.relayra.message.dto;

import com.relayra.auth.dto.UserSummary;
import com.relayra.message.domain.MessageType;
import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
    UUID id,
    String clientMessageId,
    UUID channelId,
    UUID conversationId,
    UserSummary author,
    String content,
    MessageType type,
    UUID replyToMessageId,
    Instant createdAt,
    Instant editedAt,
    Instant deletedAt) {}
