package com.relayra.reaction.dto;

import com.relayra.auth.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record ReactionEventData(
    UUID messageId,
    UUID channelId,
    UUID conversationId,
    String emoji,
    UserSummary user,
    int count,
    Instant reactedAt) {}
