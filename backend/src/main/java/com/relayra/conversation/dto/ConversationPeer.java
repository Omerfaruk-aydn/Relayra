package com.relayra.conversation.dto;

import java.util.UUID;

public record ConversationPeer(UUID userId, String username, String displayName) {}
