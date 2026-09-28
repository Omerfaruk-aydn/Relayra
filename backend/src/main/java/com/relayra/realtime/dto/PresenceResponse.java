package com.relayra.realtime.dto;

import java.util.UUID;

public record PresenceResponse(
    UUID userId, String username, String displayName, String status) {}
