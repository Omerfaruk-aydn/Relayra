package com.relayra.moderation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "community_bans",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_community_bans_pair", columnNames = {"community_id", "user_id"})
    })
public class CommunityBan {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "banned_by", nullable = false, updatable = false)
  private UUID bannedBy;

  @Column(name = "reason", length = 1000)
  private String reason;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CommunityBan() {}

  public CommunityBan(UUID id, UUID communityId, UUID userId, UUID bannedBy) {
    this.id = id;
    this.communityId = communityId;
    this.userId = userId;
    this.bannedBy = bannedBy;
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

  public UUID getUserId() {
    return userId;
  }

  public UUID getBannedBy() {
    return bannedBy;
  }

  public String getReason() {
    return reason;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void configure(String reason, Instant expiresAt) {
    this.reason = reason;
    this.expiresAt = expiresAt;
  }

  public boolean isExpired(Instant now) {
    return expiresAt != null && now.isAfter(expiresAt);
  }
}
