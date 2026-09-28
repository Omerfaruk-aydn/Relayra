package com.relayra.attachment.persistence;

import com.relayra.attachment.domain.Attachment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

  List<Attachment> findByMessageId(UUID messageId);

  List<Attachment> findByMessageIdIn(List<UUID> messageIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Attachment a where a.id = :id")
  Optional<Attachment> findByIdForUpdate(@Param("id") UUID id);
}
