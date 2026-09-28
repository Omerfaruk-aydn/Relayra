package com.relayra.message.persistence;

import com.relayra.message.domain.Message;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

  Optional<Message> findByAuthorIdAndClientMessageId(UUID authorId, String clientMessageId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Message m where m.id = :id")
  Optional<Message> findByIdForUpdate(@Param("id") UUID id);

  @Query(
      """
      select m from Message m
      where m.channelId = :channelId
        and (:beforeCreatedAt is null
          or m.createdAt < :beforeCreatedAt
          or (m.createdAt = :beforeCreatedAt and m.id < :beforeId))
      order by m.createdAt desc, m.id desc
      """)
  List<Message> findChannelHistory(
      @Param("channelId") UUID channelId,
      @Param("beforeCreatedAt") Instant beforeCreatedAt,
      @Param("beforeId") UUID beforeId,
      Pageable pageable);

  @Query(
      """
      select m from Message m
      where m.conversationId = :conversationId
        and (:beforeCreatedAt is null
          or m.createdAt < :beforeCreatedAt
          or (m.createdAt = :beforeCreatedAt and m.id < :beforeId))
      order by m.createdAt desc, m.id desc
      """)
  List<Message> findConversationHistory(
      @Param("conversationId") UUID conversationId,
      @Param("beforeCreatedAt") Instant beforeCreatedAt,
      @Param("beforeId") UUID beforeId,
      Pageable pageable);
}
