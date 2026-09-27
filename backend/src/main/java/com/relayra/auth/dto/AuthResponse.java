package com.relayra.auth.dto;

public record AuthResponse(String accessToken, long expiresInSeconds, UserSummary user) {}
