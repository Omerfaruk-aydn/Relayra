package com.relayra.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "conversations",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_conversations_direct_pair", columnNames = {"direct_pair_key"})
    })
public class Conversation {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "type", length = 32, nullable = false, updatable = false)
  private String type = "DIRECT";

  @Column(name = "direct_pair_key", length = 128, updatable = false)
  private String directPairKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Conversation() {}

  public Conversation(UUID id, String directPairKey) {
    this.id = id;
    this.directPairKey = directPairKey;
  }

  public static String directPairKey(UUID first, UUID second) {
    return first.compareTo(second) < 0 ? first + ":" + second : second + ":" + first;
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

  public String getType() {
    return type;
  }

  public String getDirectPairKey() {
    return directPairKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
