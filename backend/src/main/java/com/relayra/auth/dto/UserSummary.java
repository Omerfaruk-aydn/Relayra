package com.relayra.auth.dto;

import java.util.UUID;

public record UserSummary(UUID id, String username, String displayName) {}
