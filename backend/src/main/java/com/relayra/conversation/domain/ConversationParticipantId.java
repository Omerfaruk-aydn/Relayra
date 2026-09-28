package com.relayra.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ConversationParticipantId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "conversation_id", nullable = false, updatable = false)
  private UUID conversationId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  protected ConversationParticipantId() {}

  public ConversationParticipantId(UUID conversationId, UUID userId) {
    this.conversationId = conversationId;
    this.userId = userId;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public UUID getUserId() {
    return userId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ConversationParticipantId that)) {
      return false;
    }
    return Objects.equals(conversationId, that.conversationId)
        && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(conversationId, userId);
  }
}
