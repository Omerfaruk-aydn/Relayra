package com.relayra.moderation.persistence;

import com.relayra.moderation.domain.CommunityBan;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityBanRepository extends JpaRepository<CommunityBan, UUID> {

  Optional<CommunityBan> findByCommunityIdAndUserId(UUID communityId, UUID userId);

  List<CommunityBan> findByCommunityIdOrderByCreatedAtDescIdDesc(UUID communityId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from CommunityBan b where b.communityId = :communityId and b.userId = :userId")
  Optional<CommunityBan> findByCommunityIdAndUserIdForUpdate(
      @Param("communityId") UUID communityId, @Param("userId") UUID userId);
}
