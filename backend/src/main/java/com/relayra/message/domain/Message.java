package com.relayra.message.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "messages",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_messages_author_client",
          columnNames = {"author_id", "client_message_id"})
    })
public class Message {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "author_id", nullable = false, updatable = false)
  private UUID authorId;

  @Column(name = "channel_id", updatable = false)
  private UUID channelId;

  @Column(name = "conversation_id", updatable = false)
  private UUID conversationId;

  @Column(name = "reply_to_message_id", updatable = false)
  private UUID replyToMessageId;

  @Column(name = "content", length = 4000)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", length = 32, nullable = false, updatable = false)
  private MessageType type;

  @Column(name = "client_message_id", length = 128, nullable = false, updatable = false)
  private String clientMessageId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "edited_at")
  private Instant editedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected Message() {}

  public Message(
      UUID id,
      UUID authorId,
      UUID channelId,
      UUID conversationId,
      UUID replyToMessageId,
      String content,
      MessageType type,
      String clientMessageId) {
    this.id = id;
    this.authorId = authorId;
    this.channelId = channelId;
    this.conversationId = conversationId;
    this.replyToMessageId = replyToMessageId;
    this.content = content;
    this.type = type;
    this.clientMessageId = clientMessageId;
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

  public UUID getAuthorId() {
    return authorId;
  }

  public UUID getChannelId() {
    return channelId;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public UUID getReplyToMessageId() {
    return replyToMessageId;
  }

  public String getContent() {
    return content;
  }

  public MessageType getType() {
    return type;
  }

  public String getClientMessageId() {
    return clientMessageId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getEditedAt() {
    return editedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void edit(String content) {
    if (deletedAt != null) {
      throw new IllegalStateException("Deleted message cannot be edited.");
    }
    this.content = content;
    this.editedAt = Instant.now();
  }

  public void softDelete() {
    if (deletedAt == null) {
      this.content = null;
      this.deletedAt = Instant.now();
    }
  }
}
