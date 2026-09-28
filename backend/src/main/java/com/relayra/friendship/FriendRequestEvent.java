package com.relayra.friendship;

import java.util.UUID;

public record FriendRequestEvent(UUID requestId, UUID senderId, UUID receiverId) {}
