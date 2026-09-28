package com.relayra.auth.persistence;

import com.relayra.auth.domain.Profile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

  Optional<Profile> findByUserId(UUID userId);

  List<Profile> findByUserIdIn(List<UUID> userIds);
}
