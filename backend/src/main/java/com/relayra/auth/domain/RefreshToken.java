package com.relayra.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, unique = true, updatable = false)
  private String tokenHash;

  @Column(name = "token_family_id", nullable = false, updatable = false)
  private UUID tokenFamilyId;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "replaced_by")
  private UUID replacedBy;

  @Column(name = "user_agent_hash")
  private String userAgentHash;

  @Column(name = "ip_hash")
  private String ipHash;

  protected RefreshToken() {}

  public RefreshToken(
      UUID id,
      UUID userId,
      String tokenHash,
      UUID tokenFamilyId,
      Instant expiresAt,
      String userAgentHash,
      String ipHash) {
    this.id = id;
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.tokenFamilyId = tokenFamilyId;
    this.expiresAt = expiresAt;
    this.userAgentHash = userAgentHash;
    this.ipHash = ipHash;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public UUID getTokenFamilyId() {
    return tokenFamilyId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public UUID getReplacedBy() {
    return replacedBy;
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpired(Instant now) {
    return now.isAfter(expiresAt);
  }

  public void revoke(UUID replacementId, Instant now) {
    this.revokedAt = now;
    this.replacedBy = replacementId;
  }

  public void revoke(Instant now) {
    this.revokedAt = now;
  }
}
