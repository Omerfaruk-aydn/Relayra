package com.relayra.reaction.domain;

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
    name = "message_reactions",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_message_reactions",
          columnNames = {"message_id", "user_id", "emoji"})
    })
public class MessageReaction {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "message_id", nullable = false, updatable = false)
  private UUID messageId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "emoji", length = 64, nullable = false, updatable = false)
  private String emoji;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MessageReaction() {}

  public MessageReaction(UUID id, UUID messageId, UUID userId, String emoji) {
    this.id = id;
    this.messageId = messageId;
    this.userId = userId;
    this.emoji = emoji;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getMessageId() {
    return messageId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getEmoji() {
    return emoji;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
