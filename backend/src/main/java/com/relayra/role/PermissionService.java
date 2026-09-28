package com.relayra.role;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.community.domain.Community;
import com.relayra.community.domain.CommunityMember;
import com.relayra.community.domain.MemberStatus;
import com.relayra.community.persistence.CommunityMemberRepository;
import com.relayra.community.persistence.CommunityRepository;
import com.relayra.moderation.persistence.CommunityBanRepository;
import com.relayra.role.domain.MemberRole;
import com.relayra.role.domain.Permission;
import com.relayra.role.domain.Role;
import com.relayra.role.persistence.MemberRoleRepository;
import com.relayra.role.persistence.RoleRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {

  private final CommunityRepository communities;
  private final CommunityMemberRepository members;
  private final CommunityBanRepository bans;
  private final RoleRepository roles;
  private final MemberRoleRepository memberRoles;

  public PermissionService(
      CommunityRepository communities,
      CommunityMemberRepository members,
      CommunityBanRepository bans,
      RoleRepository roles,
      MemberRoleRepository memberRoles) {
    this.communities = communities;
    this.members = members;
    this.bans = bans;
    this.roles = roles;
    this.memberRoles = memberRoles;
  }

  @Transactional(readOnly = true)
  public Set<Permission> resolve(UUID userId, UUID communityId) {
    Community community = requireCommunity(communityId);
    requireNotBanned(communityId, userId);
    CommunityMember membership = requireActiveMembershipValue(userId, communityId);
    if (community.getOwnerId().equals(userId)) {
      return Set.copyOf(EnumSet.allOf(Permission.class));
    }
    EnumSet<Permission> effective = EnumSet.noneOf(Permission.class);
    roles.findByCommunityIdAndName(communityId, "@everyone")
        .ifPresent(role -> effective.addAll(role.getPermissions()));
    List<UUID> roleIds =
        memberRoles.findByIdCommunityMemberId(membership.getId()).stream()
            .map(MemberRole::getId)
            .map(id -> id.getRoleId())
            .toList();
    roles.findAllById(roleIds).stream()
        .filter(role -> role.getCommunityId().equals(communityId))
        .forEach(role -> effective.addAll(role.getPermissions()));
    return Set.copyOf(effective);
  }

  @Transactional(readOnly = true)
  public boolean has(UUID userId, UUID communityId, Permission permission) {
    try {
      return resolve(userId, communityId).contains(permission);
    } catch (DomainException exception) {
      return false;
    }
  }

  @Transactional(readOnly = true)
  public void require(UUID userId, UUID communityId, Permission permission) {
    if (!resolve(userId, communityId).contains(permission)) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.INSUFFICIENT_PERMISSION,
          "You do not have permission to perform this action.");
    }
  }

  @Transactional(readOnly = true)
  public int highestRolePosition(UUID userId, UUID communityId) {
    Community community = requireCommunity(communityId);
    CommunityMember membership = requireActiveMembershipValue(userId, communityId);
    if (community.getOwnerId().equals(userId)) {
      return Integer.MAX_VALUE;
    }
    List<UUID> roleIds =
        memberRoles.findByIdCommunityMemberId(membership.getId()).stream()
            .map(MemberRole::getId)
            .map(id -> id.getRoleId())
            .toList();
    return roles.findAllById(roleIds).stream()
        .filter(role -> role.getCommunityId().equals(communityId))
        .mapToInt(Role::getPosition)
        .max()
        .orElse(0);
  }

  private Community requireCommunity(UUID communityId) {
    return communities
        .findById(communityId)
        .orElseThrow(
            () ->
                new DomainException(
                    HttpStatus.NOT_FOUND.value(),
                    ErrorCodes.RESOURCE_NOT_FOUND,
                    "Community was not found."));
  }

  private void requireActiveMembership(UUID userId, UUID communityId) {
    requireNotBanned(communityId, userId);
    requireActiveMembershipValue(userId, communityId);
  }

  private CommunityMember requireActiveMembershipValue(UUID userId, UUID communityId) {
    return members
        .findByCommunityIdAndUserId(communityId, userId)
        .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
        .orElseThrow(
            () ->
                new DomainException(
                    HttpStatus.FORBIDDEN.value(),
                    ErrorCodes.ACCESS_DENIED,
                    "Community membership is required."));
  }

  private void requireNotBanned(UUID communityId, UUID userId) {
    boolean banned =
        bans.findByCommunityIdAndUserId(communityId, userId)
            .filter(ban -> !ban.isExpired(Instant.now()))
            .isPresent();
    if (banned) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.USER_BANNED,
          "You are banned from this community.");
    }
  }
}
