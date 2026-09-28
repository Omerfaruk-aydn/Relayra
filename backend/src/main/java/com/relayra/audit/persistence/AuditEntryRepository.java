package com.relayra.audit.persistence;

import com.relayra.audit.domain.AuditEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {

  @Query(
      """
      select a from AuditEntry a
      where a.communityId = :communityId
        and (:beforeCreatedAt is null
          or a.createdAt < :beforeCreatedAt
          or (a.createdAt = :beforeCreatedAt and a.id < :beforeId))
      order by a.createdAt desc, a.id desc
      """)
  List<AuditEntry> findCommunityHistory(
      @Param("communityId") UUID communityId,
      @Param("beforeCreatedAt") java.time.Instant beforeCreatedAt,
      @Param("beforeId") UUID beforeId,
      Pageable pageable);
}
