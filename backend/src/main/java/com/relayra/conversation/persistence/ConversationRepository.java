package com.relayra.conversation.persistence;

import com.relayra.conversation.domain.Conversation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  Optional<Conversation> findByDirectPairKey(String directPairKey);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Conversation c where c.directPairKey = :directPairKey")
  Optional<Conversation> findByDirectPairKeyForUpdate(@Param("directPairKey") String directPairKey);
}
