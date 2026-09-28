package com.relayra.realtime.dto;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEvent<T>(UUID eventId, String type, Instant occurredAt, T data) {}
