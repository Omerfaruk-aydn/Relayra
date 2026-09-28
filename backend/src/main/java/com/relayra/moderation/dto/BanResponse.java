package com.relayra.moderation.dto;

import java.time.Instant;
import java.util.UUID;

public record BanResponse(
    UUID id,
    UUID communityId,
    UUID userId,
    UUID bannedBy,
    String reason,
    Instant expiresAt,
    Instant createdAt) {}
