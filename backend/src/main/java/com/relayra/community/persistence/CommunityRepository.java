package com.relayra.community.persistence;

import com.relayra.community.domain.Community;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityRepository extends JpaRepository<Community, UUID> {

  List<Community> findByOwnerId(UUID ownerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Community c where c.id = :id")
  Optional<Community> findByIdForUpdate(@Param("id") UUID id);
}
