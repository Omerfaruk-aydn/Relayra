package com.relayra.friendship.domain;

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
    name = "user_blocks",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_user_blocks_pair", columnNames = {"blocker_id", "blocked_id"})
    })
public class UserBlock {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "blocker_id", nullable = false, updatable = false)
  private UUID blockerId;

  @Column(name = "blocked_id", nullable = false, updatable = false)
  private UUID blockedId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected UserBlock() {}

  public UserBlock(UUID id, UUID blockerId, UUID blockedId) {
    if (blockerId.equals(blockedId)) {
      throw new IllegalArgumentException("Cannot block yourself.");
    }
    this.id = id;
    this.blockerId = blockerId;
    this.blockedId = blockedId;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getBlockerId() {
    return blockerId;
  }

  public UUID getBlockedId() {
    return blockedId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
