package com.relayra.realtime.dto;

import java.time.Instant;
import java.util.UUID;

public record PresenceEventData(UUID userId, String status, Instant changedAt) {}
