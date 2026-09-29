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

  @org.springframework.data.jpa.repository.Query(
      "select new com.relayra.community.CommunityMemberCount(m.communityId, count(m)) "
          + "from CommunityMember m where m.communityId in :communityIds "
          + "and m.status = com.relayra.community.domain.MemberStatus.ACTIVE "
          + "group by m.communityId")
  List<com.relayra.community.CommunityMemberCount> countActiveByCommunityIds(
      @org.springframework.data.repository.query.Param("communityIds") List<UUID> communityIds);

  void deleteByCommunityIdAndUserId(UUID communityId, UUID userId);
}
