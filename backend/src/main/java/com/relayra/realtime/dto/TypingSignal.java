package com.relayra.realtime.dto;

import java.util.UUID;

public record TypingSignal(UUID userId, UUID channelId, boolean typing) {}
