package com.relayra.conversation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
    UUID id, String type, List<ConversationPeer> participants, Instant createdAt) {}
