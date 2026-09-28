package com.relayra.role.persistence;

import com.relayra.role.domain.MemberRole;
import com.relayra.role.domain.MemberRoleId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRoleRepository extends JpaRepository<MemberRole, MemberRoleId> {

  List<MemberRole> findByIdCommunityMemberId(UUID communityMemberId);

  List<MemberRole> findByIdRoleId(UUID roleId);

  void deleteByIdCommunityMemberId(UUID communityMemberId);

  void deleteByIdRoleId(UUID roleId);
}
