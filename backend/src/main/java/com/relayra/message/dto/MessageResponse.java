package com.relayra.message.dto;

import com.relayra.auth.dto.UserSummary;
import com.relayra.message.domain.MessageType;
import com.relayra.reaction.dto.ReactionResponse;
import java.time.Instant;
import java.util.List;
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
    List<ReactionResponse> reactions,
    Instant createdAt,
    Instant editedAt,
    Instant deletedAt) {}
