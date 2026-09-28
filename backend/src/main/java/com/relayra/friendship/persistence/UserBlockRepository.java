package com.relayra.friendship.persistence;

import com.relayra.friendship.domain.UserBlock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {

  boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

  List<UserBlock> findByBlockerId(UUID blockerId);

  void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}
