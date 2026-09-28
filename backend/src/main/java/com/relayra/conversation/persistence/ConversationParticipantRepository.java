package com.relayra.conversation.persistence;

import com.relayra.conversation.domain.ConversationParticipant;
import com.relayra.conversation.domain.ConversationParticipantId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationParticipantRepository
    extends JpaRepository<ConversationParticipant, ConversationParticipantId> {

  List<ConversationParticipant> findByIdUserId(UUID userId);

  @Query(
      "select p.id.userId from ConversationParticipant p where p.id.conversationId = :conversationId")
  List<UUID> findUserIdsByConversationId(@Param("conversationId") UUID conversationId);

  @Query(
      "select case when count(p) > 0 then true else false end from ConversationParticipant p"
          + " where p.id.conversationId = :conversationId and p.id.userId = :userId")
  boolean existsByConversationIdAndUserId(
      @Param("conversationId") UUID conversationId, @Param("userId") UUID userId);
}
