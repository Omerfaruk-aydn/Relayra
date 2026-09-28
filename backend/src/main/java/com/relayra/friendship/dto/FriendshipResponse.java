package com.relayra.friendship.dto;

import com.relayra.friendship.domain.FriendshipStatus;
import java.time.Instant;
import java.util.UUID;

public record FriendshipResponse(
    UUID id,
    UUID senderId,
    UUID receiverId,
    FriendshipStatus status,
    Instant createdAt,
    Instant updatedAt) {}
