package com.relayra.community.persistence;

import com.relayra.community.domain.CommunityMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, UUID> {

  Optional<CommunityMember> findByCommunityIdAndUserId(UUID communityId, UUID userId);

  List<CommunityMember> findByCommunityId(UUID communityId);

  List<CommunityMember> findByUserId(UUID userId);

  List<CommunityMember> findByUserIdAndStatus(
      UUID userId, com.relayra.community.domain.MemberStatus status);

  long countByCommunityIdAndStatus(
      UUID communityId, com.relayra.community.domain.MemberStatus status);

  boolean existsByCommunityIdAndUserId(UUID communityId, UUID userId);

  void deleteByCommunityIdAndUserId(UUID communityId, UUID userId);
}
