package com.relayra.channel.persistence;

import com.relayra.channel.domain.Channel;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelRepository extends JpaRepository<Channel, UUID> {

  List<Channel> findByCommunityIdOrderByPositionAscIdAsc(UUID communityId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Channel c where c.id = :id")
  Optional<Channel> findByIdForUpdate(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Channel c where c.communityId = :communityId order by c.position")
  List<Channel> findByCommunityIdForUpdate(@Param("communityId") UUID communityId);

  void deleteByCommunityId(UUID communityId);
}
