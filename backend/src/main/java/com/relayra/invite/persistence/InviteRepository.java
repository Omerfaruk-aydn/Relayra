package com.relayra.invite.persistence;

import com.relayra.invite.domain.Invite;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InviteRepository extends JpaRepository<Invite, UUID> {

  Optional<Invite> findByCode(String code);

  List<Invite> findByCommunityId(UUID communityId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Invite i where i.code = :code")
  Optional<Invite> findByCodeForUpdate(@Param("code") String code);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Invite i where i.id = :id")
  Optional<Invite> findByIdForUpdate(@Param("id") UUID id);
}
