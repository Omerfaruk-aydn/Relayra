package com.relayra.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "relayra.auth")
public record AuthProperties(
    String jwtSigningKey,
    long accessTokenTtlSeconds,
    long refreshTokenTtlSeconds,
    boolean secureCookies) {}
