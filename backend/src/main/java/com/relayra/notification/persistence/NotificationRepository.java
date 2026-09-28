package com.relayra.notification.persistence;

import com.relayra.notification.domain.Notification;
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

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  @Query(
      """
      select n from Notification n
      where n.userId = :userId
        and (:beforeCreatedAt is null
          or n.createdAt < :beforeCreatedAt
          or (n.createdAt = :beforeCreatedAt and n.id < :beforeId))
      order by n.createdAt desc, n.id desc
      """)
  List<Notification> findUserHistory(
      @Param("userId") UUID userId,
      @Param("beforeCreatedAt") Instant beforeCreatedAt,
      @Param("beforeId") UUID beforeId,
      Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from Notification n where n.id = :id")
  Optional<Notification> findByIdForUpdate(@Param("id") UUID id);

  Optional<Notification> findByUserIdAndTypeAndActorIdAndMessageId(
      UUID userId,
      com.relayra.notification.domain.NotificationType type,
      UUID actorId,
      UUID messageId);

  long countByUserIdAndReadAtIsNull(UUID userId);

  List<Notification> findByUserIdAndReadAtIsNull(UUID userId);
}
