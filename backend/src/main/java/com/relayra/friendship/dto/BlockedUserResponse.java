package com.relayra.friendship.dto;

import java.time.Instant;
import java.util.UUID;

public record BlockedUserResponse(UUID userId, String username, Instant blockedAt) {}
