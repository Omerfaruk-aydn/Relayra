package com.relayra.community.dto;

import java.time.Instant;
import java.util.UUID;

public record CommunityResponse(
    UUID id,
    UUID ownerId,
    String name,
    String description,
    String iconKey,
    long memberCount,
    Instant createdAt,
    Instant updatedAt) {}
