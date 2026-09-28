package com.relayra.role.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MemberRoleId implements Serializable {

  @Column(name = "community_member_id", nullable = false)
  private UUID communityMemberId;

  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  protected MemberRoleId() {}

  public MemberRoleId(UUID communityMemberId, UUID roleId) {
    this.communityMemberId = communityMemberId;
    this.roleId = roleId;
  }

  public UUID getCommunityMemberId() {
    return communityMemberId;
  }

  public UUID getRoleId() {
    return roleId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof MemberRoleId that)) {
      return false;
    }
    return communityMemberId.equals(that.communityMemberId) && roleId.equals(that.roleId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(communityMemberId, roleId);
  }
}
