package com.relayra.community.persistence;

import com.relayra.community.domain.Community;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityRepository extends JpaRepository<Community, UUID> {

  List<Community> findByOwnerId(UUID ownerId);
}
