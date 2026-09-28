package com.relayra.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "notifications",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_notifications_dedup",
          columnNames = {"user_id", "type", "actor_id", "message_id"})
    })
public class Notification {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", length = 32, nullable = false, updatable = false)
  private NotificationType type;

  @Column(name = "actor_id", updatable = false)
  private UUID actorId;

  @Column(name = "community_id", updatable = false)
  private UUID communityId;

  @Column(name = "channel_id", updatable = false)
  private UUID channelId;

  @Column(name = "conversation_id", updatable = false)
  private UUID conversationId;

  @Column(name = "message_id", updatable = false)
  private UUID messageId;

  @Column(name = "reference_id", updatable = false)
  private UUID referenceId;

  @Column(name = "detail", length = 1000, updatable = false)
  private String detail;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Notification() {}

  public Notification(UUID id, UUID userId, NotificationType type) {
    this.id = id;
    this.userId = userId;
    this.type = type;
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

  public NotificationType getType() {
    return type;
  }

  public UUID getActorId() {
    return actorId;
  }

  public UUID getCommunityId() {
    return communityId;
  }

  public UUID getChannelId() {
    return channelId;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public UUID getMessageId() {
    return messageId;
  }

  public UUID getReferenceId() {
    return referenceId;
  }

  public String getDetail() {
    return detail;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void link(
      UUID actorId,
      UUID communityId,
      UUID channelId,
      UUID conversationId,
      UUID messageId,
      UUID referenceId,
      String detail) {
    this.actorId = actorId;
    this.communityId = communityId;
    this.channelId = channelId;
    this.conversationId = conversationId;
    this.messageId = messageId;
    this.referenceId = referenceId;
    this.detail = detail;
  }

  public void markRead() {
    if (this.readAt == null) {
      this.readAt = Instant.now();
    }
  }
}
