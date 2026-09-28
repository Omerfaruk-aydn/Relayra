package com.relayra.reaction.dto;

import java.time.Instant;
import java.util.UUID;

public record ReactionResponse(UUID messageId, String emoji, int count, boolean mine) {}
