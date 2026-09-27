package com.relayra.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private static final String ISSUER = "relayra";

  private final SecretKey key;
  private final AuthProperties properties;

  public JwtService(AuthProperties properties) {
    byte[] keyBytes = properties.jwtSigningKey().getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalStateException("JWT signing key must be at least 32 bytes.");
    }
    this.key = Keys.hmacShaKeyFor(keyBytes);
    this.properties = properties;
  }

  public String generateAccessToken(UUID userId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(ISSUER)
        .subject(userId.toString())
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(properties.accessTokenTtlSeconds())))
        .signWith(key)
        .compact();
  }

  public UUID parseUserId(String token) {
    String subject =
        Jwts.parser()
            .verifyWith(key)
            .requireIssuer(ISSUER)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    return UUID.fromString(subject);
  }
}
