package com.relayra.reaction.persistence;

import com.relayra.reaction.domain.MessageReaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, UUID> {

  Optional<MessageReaction> findByMessageIdAndUserIdAndEmoji(
      UUID messageId, UUID userId, String emoji);

  List<MessageReaction> findByMessageId(UUID messageId);

  List<MessageReaction> findByMessageIdIn(List<UUID> messageIds);

  long deleteByMessageIdAndUserIdAndEmoji(UUID messageId, UUID userId, String emoji);
}
