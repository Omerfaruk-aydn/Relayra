package com.relayra.invite.dto;

import java.time.Instant;
import java.util.UUID;

public record InviteResponse(
    UUID id,
    UUID communityId,
    String code,
    Integer maxUses,
    int usageCount,
    Instant expiresAt,
    Instant revokedAt,
    Instant createdAt) {}
