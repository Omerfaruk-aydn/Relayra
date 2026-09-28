package com.relayra.message.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessagePageResponse(
    List<MessageResponse> messages, Instant nextBeforeCreatedAt, UUID nextBeforeId) {}
