package com.relayra.friendship.dto;

import java.time.Instant;
import java.util.UUID;

public record FriendResponse(
    UUID userId, String username, String displayName, Instant friendsSince) {}
