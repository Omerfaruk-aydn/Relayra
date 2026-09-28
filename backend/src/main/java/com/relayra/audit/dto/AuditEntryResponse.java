package com.relayra.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(
    UUID id,
    UUID communityId,
    UUID actorId,
    String action,
    UUID targetUserId,
    UUID targetId,
    String detail,
    Instant createdAt) {}
