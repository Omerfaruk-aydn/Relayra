package com.relayra.friendship.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "friendships")
public class Friendship {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "sender_id", nullable = false, updatable = false)
  private UUID senderId;

  @Column(name = "receiver_id", nullable = false, updatable = false)
  private UUID receiverId;

  @Column(name = "user_low_id", nullable = false, updatable = false)
  private UUID userLowId;

  @Column(name = "user_high_id", nullable = false, updatable = false)
  private UUID userHighId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 32, nullable = false)
  private FriendshipStatus status = FriendshipStatus.PENDING;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected Friendship() {}

  public Friendship(UUID id, UUID senderId, UUID receiverId) {
    if (senderId.equals(receiverId)) {
      throw new IllegalArgumentException("Sender and receiver must differ.");
    }
    this.id = id;
    this.senderId = senderId;
    this.receiverId = receiverId;
    if (senderId.compareTo(receiverId) < 0) {
      this.userLowId = senderId;
      this.userHighId = receiverId;
    } else {
      this.userLowId = receiverId;
      this.userHighId = senderId;
    }
    this.status = FriendshipStatus.PENDING;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getSenderId() {
    return senderId;
  }

  public UUID getReceiverId() {
    return receiverId;
  }

  public UUID getUserLowId() {
    return userLowId;
  }

  public UUID getUserHighId() {
    return userHighId;
  }

  public FriendshipStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void accept() {
    requirePending();
    this.status = FriendshipStatus.ACCEPTED;
  }

  public void reject() {
    requirePending();
    this.status = FriendshipStatus.REJECTED;
  }

  public void cancel() {
    requirePending();
    this.status = FriendshipStatus.CANCELLED;
  }

  private void requirePending() {
    if (this.status != FriendshipStatus.PENDING) {
      throw new IllegalStateException("Only pending requests can transition.");
    }
  }
}
