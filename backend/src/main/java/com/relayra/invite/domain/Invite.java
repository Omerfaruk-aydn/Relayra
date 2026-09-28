package com.relayra.invite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invites")
public class Invite {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Column(name = "code", length = 64, nullable = false, unique = true, updatable = false)
  private String code;

  @Column(name = "max_uses")
  private Integer maxUses;

  @Column(name = "usage_count", nullable = false)
  private int usageCount;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected Invite() {}

  public Invite(UUID id, UUID communityId, UUID createdBy, String code) {
    this.id = id;
    this.communityId = communityId;
    this.createdBy = createdBy;
    this.code = code;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getCommunityId() {
    return communityId;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public String getCode() {
    return code;
  }

  public Integer getMaxUses() {
    return maxUses;
  }

  public int getUsageCount() {
    return usageCount;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void configure(Integer maxUses, Instant expiresAt) {
    if (maxUses != null && maxUses <= 0) {
      throw new IllegalArgumentException("maxUses must be positive.");
    }
    this.maxUses = maxUses;
    this.expiresAt = expiresAt;
  }

  public void revoke() {
    if (this.revokedAt != null) {
      return;
    }
    this.revokedAt = Instant.now();
  }

  public void recordUse() {
    if (revokedAt != null) {
      throw new IllegalStateException("Invite is revoked.");
    }
    if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
      throw new IllegalStateException("Invite has expired.");
    }
    if (maxUses != null && usageCount >= maxUses) {
      throw new IllegalStateException("Invite is exhausted.");
    }
    this.usageCount++;
  }
}
