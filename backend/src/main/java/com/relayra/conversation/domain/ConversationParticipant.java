package com.relayra.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_participants")
public class ConversationParticipant {

  @EmbeddedId private ConversationParticipantId id;

  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  @Column(name = "last_read_message_id")
  private UUID lastReadMessageId;

  protected ConversationParticipant() {}

  public ConversationParticipant(UUID conversationId, UUID userId) {
    this.id = new ConversationParticipantId(conversationId, userId);
  }

  @PrePersist
  void onCreate() {
    this.joinedAt = Instant.now();
  }

  public ConversationParticipantId getId() {
    return id;
  }

  public UUID getConversationId() {
    return id.getConversationId();
  }

  public UUID getUserId() {
    return id.getUserId();
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public UUID getLastReadMessageId() {
    return lastReadMessageId;
  }
}
