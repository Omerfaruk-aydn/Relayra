package com.relayra.user.dto;

import java.time.Instant;
import java.util.UUID;

public record PublicUserProfileResponse(
    UUID id, String username, String displayName, String bio, Instant createdAt) {}
