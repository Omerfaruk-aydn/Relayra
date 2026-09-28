package com.relayra.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String username,
    String displayName,
    String bio,
    String avatarKey,
    String bannerKey,
    String timezone,
    Instant createdAt) {}
