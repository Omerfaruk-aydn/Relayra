package com.relayra.audit.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditPageResponse(
    List<AuditEntryResponse> entries, Instant beforeCreatedAt, UUID beforeId) {}
