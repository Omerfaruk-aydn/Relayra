package com.relayra.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditEntry {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Column(name = "actor_id", nullable = false, updatable = false)
  private UUID actorId;

  @Column(name = "action", length = 64, nullable = false, updatable = false)
  private String action;

  @Column(name = "target_user_id", updatable = false)
  private UUID targetUserId;

  @Column(name = "target_id", updatable = false)
  private UUID targetId;

  @Column(name = "detail", length = 1000, updatable = false)
  private String detail;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AuditEntry() {}

  public AuditEntry(UUID id, UUID communityId, UUID actorId, String action) {
    this.id = id;
    this.communityId = communityId;
    this.actorId = actorId;
    this.action = action;
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

  public UUID getActorId() {
    return actorId;
  }

  public String getAction() {
    return action;
  }

  public UUID getTargetUserId() {
    return targetUserId;
  }

  public UUID getTargetId() {
    return targetId;
  }

  public String getDetail() {
    return detail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void describe(UUID targetUserId, UUID targetId, String detail) {
    this.targetUserId = targetUserId;
    this.targetId = targetId;
    this.detail = detail;
  }
}
