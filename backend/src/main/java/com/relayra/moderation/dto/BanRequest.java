package com.relayra.moderation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record BanRequest(
    @NotNull(message = "userId is required.") UUID userId,
    @Size(max = 1000, message = "Reason must be at most 1000 characters.") String reason,
    Instant expiresAt) {}
