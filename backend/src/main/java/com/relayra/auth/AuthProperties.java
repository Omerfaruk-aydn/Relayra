package com.relayra.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "relayra.auth")
public record AuthProperties(
    String jwtSigningKey,
    long accessTokenTtlSeconds,
    long refreshTokenTtlSeconds,
    boolean secureCookies,
    @DefaultValue("3") int registerPerHourPerIp,
    @DefaultValue("5") int loginPerMinutePerIp,
    @DefaultValue("30") int refreshPerMinutePerIp) {}

