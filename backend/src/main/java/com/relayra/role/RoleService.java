package com.relayra.role;

import com.relayra.auth.RateLimitedException;
import com.relayra.auth.RateLimiter;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.community.domain.Community;
import com.relayra.community.domain.CommunityMember;
import com.relayra.community.domain.MemberStatus;
import com.relayra.community.persistence.CommunityMemberRepository;
import com.relayra.community.persistence.CommunityRepository;
import com.relayra.role.domain.MemberRole;
import com.relayra.role.domain.MemberRoleId;
import com.relayra.role.domain.Permission;
import com.relayra.role.domain.Role;
import com.relayra.role.dto.CreateRoleRequest;
import com.relayra.role.dto.ReorderRolesRequest;
import com.relayra.role.dto.RoleResponse;
import com.relayra.role.dto.UpdateRoleRequest;
import com.relayra.role.persistence.MemberRoleRepository;
import com.relayra.role.persistence.RoleRepository;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

  private static final int MAX_ROLES_PER_COMMUNITY = 100;

  private final RoleRepository roles;
  private final MemberRoleRepository memberRoles;
  private final CommunityRepository communities;
  private final CommunityMemberRepository members;
  private final PermissionService permissions;
  private final RateLimiter rateLimiter;

  public RoleService(
      RoleRepository roles,
      MemberRoleRepository memberRoles,
      CommunityRepository communities,
      CommunityMemberRepository members,
      PermissionService permissions,
      RateLimiter rateLimiter) {
    this.roles = roles;
    this.memberRoles = memberRoles;
    this.communities = communities;
    this.members = members;
    this.permissions = permissions;
    this.rateLimiter = rateLimiter;
  }

  @Transactional
  public RoleResponse create(UUID callerId, UUID communityId, CreateRoleRequest request) {
    permissions.require(callerId, communityId, Permission.MANAGE_ROLES);
    Community communitySnapshot = requireCommunity(communityId);
    requireOwner(callerId, communitySnapshot);
    checkMutationRate(callerId);
    Community community = requireCommunityForUpdate(communityId);
    requireOwner(callerId, community);
    List<Role> locked = roles.findByCommunityIdForUpdate(communityId);
    if (locked.size() >= MAX_ROLES_PER_COMMUNITY) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(), ErrorCodes.CONFLICT, "Community role limit reached.");
    }
    String name = normalizeName(request.name());
    Set<Permission> requestedPermissions = copyPermissions(request.permissions());
    requireCanGrantPermissions(callerId, community, requestedPermissions);
    ensureUniqueName(communityId, name, null);
    int position = locked.stream().mapToInt(Role::getPosition).max().orElse(0) + 1;
    Role role = new Role(UUID.randomUUID(), communityId, name, position, false);
    role.update(name, normalizeColor(request.color()), requestedPermissions);
    try {
      roles.saveAll(locked);
      return toResponse(roles.saveAndFlush(role));
    } catch (DataIntegrityViolationException exception) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.DUPLICATE_RESOURCE,
          "A role with this name already exists.");
    }
  }

  @Transactional(readOnly = true)
  public List<RoleResponse> list(UUID callerId, UUID communityId) {
    permissions.require(callerId, communityId, Permission.VIEW_CHANNEL);
    return roles.findByCommunityIdOrderByPositionAscIdAsc(communityId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public RoleResponse update(UUID callerId, UUID roleId, UpdateRoleRequest request) {
    Role snapshot = requireRole(roleId);
    permissions.require(callerId, snapshot.getCommunityId(), Permission.MANAGE_ROLES);
    Community communitySnapshot = requireCommunity(snapshot.getCommunityId());
    requireOwner(callerId, communitySnapshot);
    checkMutationRate(callerId);
    Community community = requireCommunityForUpdate(snapshot.getCommunityId());
    requireOwner(callerId, community);
    Role role = findLockedRole(community.getId(), roleId);
    requireMutableRole(role);
    requireCanManageRole(callerId, community, role);
    String name = request.name() == null ? role.getName() : normalizeName(request.name());
    ensureUniqueName(community.getId(), name, roleId);
    String color = request.color() == null ? role.getColor() : normalizeColor(request.color());
    Set<Permission> nextPermissions =
        request.permissions() == null ? role.getPermissions() : copyPermissions(request.permissions());
    requireCanGrantPermissions(callerId, community, nextPermissions);
    role.update(name, color, nextPermissions);
    try {
      return toResponse(roles.saveAndFlush(role));
    } catch (DataIntegrityViolationException exception) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.DUPLICATE_RESOURCE,
          "A role with this name already exists.");
    }
  }

  @Transactional
  public void delete(UUID callerId, UUID roleId) {
    Role snapshot = requireRole(roleId);
    permissions.require(callerId, snapshot.getCommunityId(), Permission.MANAGE_ROLES);
    Community communitySnapshot = requireCommunity(snapshot.getCommunityId());
    requireOwner(callerId, communitySnapshot);
    checkMutationRate(callerId);
    Community community = requireCommunityForUpdate(snapshot.getCommunityId());
    requireOwner(callerId, community);
    List<Role> locked = roles.findByCommunityIdForUpdate(community.getId());
    Role role =
        locked.stream()
            .filter(candidate -> candidate.getId().equals(roleId))
            .findFirst()
            .orElseThrow(this::roleNotFound);
    requireMutableRole(role);
    requireCanManageRole(callerId, community, role);
    memberRoles.deleteByIdRoleId(roleId);
    roles.delete(role);
    int position = 0;
    for (Role remaining : locked) {
      if (!remaining.getId().equals(roleId)) {
        remaining.moveTo(position++);
      }
    }
    roles.flush();
  }

  @Transactional
  public List<RoleResponse> reorder(
      UUID callerId, UUID communityId, ReorderRolesRequest request) {
    permissions.require(callerId, communityId, Permission.MANAGE_ROLES);
    Community communitySnapshot = requireCommunity(communityId);
    requireOwner(callerId, communitySnapshot);
    checkMutationRate(callerId);
    Community community = requireCommunityForUpdate(communityId);
    requireOwner(callerId, community);
    List<Role> locked = roles.findByCommunityIdForUpdate(communityId);
    List<Role> mutable = locked.stream().filter(role -> !role.isManaged()).toList();
    List<UUID> requested = request.roleIds();
    Set<UUID> mutableIds = mutable.stream().map(Role::getId).collect(Collectors.toSet());
    if (requested.size() != mutable.size()
        || new HashSet<>(requested).size() != requested.size()
        || !new HashSet<>(requested).equals(mutableIds)) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "roleIds must contain every custom community role exactly once.");
    }
    java.util.Map<UUID, Role> byId =
        mutable.stream().collect(Collectors.toMap(Role::getId, role -> role));
    for (int index = 0; index < requested.size(); index++) {
      byId.get(requested.get(index)).moveTo(index + 1);
    }
    roles.saveAllAndFlush(mutable);
    return requested.stream().map(byId::get).map(this::toResponse).toList();
  }

  @Transactional
  public RoleResponse assign(
      UUID callerId, UUID communityId, UUID targetUserId, UUID roleId) {
    permissions.require(callerId, communityId, Permission.MANAGE_ROLES);
    checkMutationRate(callerId);
    Community community = requireCommunityForUpdate(communityId);
    permissions.require(callerId, communityId, Permission.MANAGE_ROLES);
    Role role = findLockedRole(communityId, roleId);
    requireMutableRole(role);
    requireCanGrantPermissions(callerId, community, role.getPermissions());
    CommunityMember target = requireActiveMembership(targetUserId, communityId);
    if (callerId.equals(targetUserId)) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.INSUFFICIENT_PERMISSION,
          "You cannot assign a role to yourself.");
    }
    requireCanManageTarget(callerId, community, targetUserId, role);
    MemberRoleId assignmentId = new MemberRoleId(target.getId(), roleId);
    if (!memberRoles.existsById(assignmentId)) {
      memberRoles.saveAndFlush(new MemberRole(assignmentId));
    }
    return toResponse(role);
  }

  @Transactional
  public void remove(UUID callerId, UUID communityId, UUID targetUserId, UUID roleId) {
    permissions.require(callerId, communityId, Permission.MANAGE_ROLES);
    checkMutationRate(callerId);
    Community community = requireCommunityForUpdate(communityId);
    permissions.require(callerId, communityId, Permission.MANAGE_ROLES);
    Role role = findLockedRole(communityId, roleId);
    requireMutableRole(role);
    CommunityMember target = requireActiveMembership(targetUserId, communityId);
    requireCanManageTarget(callerId, community, targetUserId, role);
    memberRoles.deleteById(new MemberRoleId(target.getId(), roleId));
  }

  @Transactional(readOnly = true)
  public List<RoleResponse> listMemberRoles(
      UUID callerId, UUID communityId, UUID targetUserId) {
    permissions.require(callerId, communityId, Permission.VIEW_CHANNEL);
    CommunityMember target = requireActiveMembership(targetUserId, communityId);
    List<UUID> roleIds =
        memberRoles.findByIdCommunityMemberId(target.getId()).stream()
            .map(MemberRole::getId)
            .map(MemberRoleId::getRoleId)
            .toList();
    return roles.findAllById(roleIds).stream()
        .filter(role -> role.getCommunityId().equals(communityId))
        .sorted(java.util.Comparator.comparingInt(Role::getPosition))
        .map(this::toResponse)
        .toList();
  }

  private void requireCanGrantPermissions(
      UUID callerId, Community community, Set<Permission> requested) {
    if (community.getOwnerId().equals(callerId)) {
      return;
    }
    Set<Permission> effective = permissions.resolve(callerId, community.getId());
    if (!effective.containsAll(requested)) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.INSUFFICIENT_PERMISSION,
          "You cannot grant permissions you do not have.");
    }
  }

  private void requireCanManageTarget(
      UUID callerId, Community community, UUID targetUserId, Role role) {
    if (community.getOwnerId().equals(targetUserId)) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(), ErrorCodes.INSUFFICIENT_PERMISSION, "Owner cannot be managed.");
    }
    if (community.getOwnerId().equals(callerId)) {
      return;
    }
    int actorPosition = permissions.highestRolePosition(callerId, community.getId());
    int targetPosition = permissions.highestRolePosition(targetUserId, community.getId());
    if (role.getPosition() >= actorPosition || targetPosition >= actorPosition) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.INSUFFICIENT_PERMISSION,
          "You cannot manage this role or member.");
    }
  }

  private void requireCanManageRole(UUID callerId, Community community, Role role) {
    if (!community.getOwnerId().equals(callerId)
        && role.getPosition() >= permissions.highestRolePosition(callerId, community.getId())) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.INSUFFICIENT_PERMISSION,
          "You cannot manage a role at or above your highest role.");
    }
  }

  private Role findLockedRole(UUID communityId, UUID roleId) {
    return roles.findByCommunityIdForUpdate(communityId).stream()
        .filter(role -> role.getId().equals(roleId))
        .findFirst()
        .orElseThrow(this::roleNotFound);
  }

  private Role requireRole(UUID roleId) {
    return roles.findById(roleId).orElseThrow(this::roleNotFound);
  }

  private Community requireCommunity(UUID communityId) {
    return communities.findById(communityId).orElseThrow(this::communityNotFound);
  }

  private Community requireCommunityForUpdate(UUID communityId) {
    return communities.findByIdForUpdate(communityId).orElseThrow(this::communityNotFound);
  }

  private CommunityMember requireActiveMembership(UUID userId, UUID communityId) {
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

  private void requireOwner(UUID callerId, Community community) {
    if (!community.getOwnerId().equals(callerId)) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.INSUFFICIENT_PERMISSION,
          "Only the community owner can manage role definitions.");
    }
  }

  private void requireMutableRole(Role role) {
    if (role.isManaged()) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.INSUFFICIENT_PERMISSION,
          "Managed roles cannot be modified or assigned.");
    }
  }

  private void ensureUniqueName(UUID communityId, String name, UUID excludedRoleId) {
    roles.findByCommunityIdAndName(communityId, name)
        .filter(role -> excludedRoleId == null || !role.getId().equals(excludedRoleId))
        .ifPresent(
            role -> {
              throw new DomainException(
                  HttpStatus.CONFLICT.value(),
                  ErrorCodes.DUPLICATE_RESOURCE,
                  "A role with this name already exists.");
            });
  }

  private String normalizeName(String value) {
    String name = value == null ? "" : value.trim();
    if (name.length() < 2 || name.length() > 64 || name.equalsIgnoreCase("@everyone")) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Role name must be between 2 and 64 characters and cannot be @everyone.");
    }
    return name;
  }

  private String normalizeColor(String value) {
    return value == null || value.isBlank() ? null : value.toUpperCase(java.util.Locale.ROOT);
  }

  private Set<Permission> copyPermissions(Set<Permission> value) {
    if (value == null) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "permissions is required.");
    }
    return value.isEmpty() ? Set.of() : Set.copyOf(value);
  }

  private void checkMutationRate(UUID callerId) {
    rateLimiter.check(
        RateLimitedException.deviceKey("role-mutate", callerId), 60, Duration.ofMinutes(1));
  }

  private DomainException roleNotFound() {
    return new DomainException(
        HttpStatus.NOT_FOUND.value(), ErrorCodes.RESOURCE_NOT_FOUND, "Role was not found.");
  }

  private DomainException communityNotFound() {
    return new DomainException(
        HttpStatus.NOT_FOUND.value(), ErrorCodes.RESOURCE_NOT_FOUND, "Community was not found.");
  }

  private RoleResponse toResponse(Role role) {
    return new RoleResponse(
        role.getId(),
        role.getCommunityId(),
        role.getName(),
        role.getPosition(),
        role.getColor(),
        role.isManaged(),
        role.getPermissions(),
        role.getCreatedAt(),
        role.getUpdatedAt());
  }
}
