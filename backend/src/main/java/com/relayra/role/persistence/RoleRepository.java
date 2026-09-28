package com.relayra.role.persistence;

import com.relayra.role.domain.Role;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {

  List<Role> findByCommunityIdOrderByPositionAscIdAsc(UUID communityId);

  Optional<Role> findByCommunityIdAndName(UUID communityId, String name);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Role r where r.communityId = :communityId order by r.position, r.id")
  List<Role> findByCommunityIdForUpdate(@Param("communityId") UUID communityId);

  void deleteByCommunityId(UUID communityId);
}
