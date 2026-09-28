package com.relayra.friendship.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SendFriendRequest(@NotNull(message = "Receiver id is required.") UUID receiverId) {}
