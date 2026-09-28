package com.relayra.moderation;

import com.relayra.audit.AuditService;
import com.relayra.auth.RateLimitedException;
import com.relayra.auth.RateLimiter;
import com.relayra.auth.domain.User;
import com.relayra.auth.domain.UserStatus;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.community.domain.Community;
import com.relayra.community.domain.CommunityMember;
import com.relayra.community.persistence.CommunityMemberRepository;
import com.relayra.community.persistence.CommunityRepository;
import com.relayra.moderation.domain.CommunityBan;
import com.relayra.moderation.dto.BanRequest;
import com.relayra.moderation.dto.BanResponse;
import com.relayra.moderation.persistence.CommunityBanRepository;
import com.relayra.role.PermissionService;
import com.relayra.role.domain.Permission;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationService {

  private final CommunityBanRepository bans;
  private final CommunityRepository communities;
  private final CommunityMemberRepository members;
  private final UserRepository users;
  private final PermissionService permissions;
  private final RateLimiter rateLimiter;
  private final AuditService audit;

  public ModerationService(
      CommunityBanRepository bans,
      CommunityRepository communities,
      CommunityMemberRepository members,
      UserRepository users,
      PermissionService permissions,
      RateLimiter rateLimiter,
      AuditService audit) {
    this.bans = bans;
    this.communities = communities;
    this.members = members;
    this.users = users;
    this.permissions = permissions;
    this.rateLimiter = rateLimiter;
    this.audit = audit;
  }

  @Transactional
  public void kick(UUID callerId, UUID communityId, UUID targetUserId) {
    requireActiveUser(callerId);
    Community community = requireCommunity(communityId);
    permissions.require(callerId, communityId, Permission.KICK_MEMBERS);
    checkMutationRate(callerId);
    Community locked = requireCommunityForUpdate(communityId);
    permissions.require(callerId, communityId, Permission.KICK_MEMBERS);
    CommunityMember target = requireMembership(targetUserId, communityId);
    requireHierarchyAllows(callerId, locked, targetUserId);
    if (locked.getOwnerId().equals(targetUserId)) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.INSUFFICIENT_PERMISSION,
          "Owner cannot be managed.");
    }
    members.deleteByCommunityIdAndUserId(communityId, targetUserId);
    audit.record(
        communityId, callerId, "KICK", targetUserId, null, "Member was removed from the community.");
  }

  @Transactional
  public BanResponse ban(UUID callerId, UUID communityId, BanRequest request) {
    requireActiveUser(callerId);
    Community community = requireCommunity(communityId);
    permissions.require(callerId, communityId, Permission.BAN_MEMBERS);
    checkMutationRate(callerId);
    Community locked = requireCommunityForUpdate(communityId);
    permissions.require(callerId, communityId, Permission.BAN_MEMBERS);
    requireHierarchyAllows(callerId, locked, request.userId());
    if (locked.getOwnerId().equals(request.userId())) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.INSUFFICIENT_PERMISSION,
          "Owner cannot be managed.");
    }
    if (callerId.equals(request.userId())) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.INSUFFICIENT_PERMISSION,
          "You cannot ban yourself.");
    }
    String reason = normalizeReason(request.reason());
    Instant expiresAt = normalizeExpiry(request.expiresAt());
    Optional<CommunityBan> lockedBan = bans.findByCommunityIdAndUserIdForUpdate(communityId, request.userId());
    if (lockedBan.isPresent()) {
      CommunityBan existing = lockedBan.get();
      if (!existing.isExpired(Instant.now())) {
        existing.configure(reason, expiresAt);
        audit.record(communityId, callerId, "BAN", request.userId(), existing.getId(), reason);
        return toResponse(existing);
      }
      bans.delete(existing);
      bans.flush();
    }
    CommunityBan ban = new CommunityBan(UUID.randomUUID(), communityId, request.userId(), callerId);
    ban.configure(reason, expiresAt);
    CommunityBan saved = bans.saveAndFlush(ban);
    members.deleteByCommunityIdAndUserId(communityId, request.userId());
    audit.record(communityId, callerId, "BAN", request.userId(), saved.getId(), reason);
    return toResponse(saved);
  }

  @Transactional
  public void unban(UUID callerId, UUID communityId, UUID targetUserId) {
    requireActiveUser(callerId);
    requireCommunity(communityId);
    permissions.require(callerId, communityId, Permission.BAN_MEMBERS);
    checkMutationRate(callerId);
    requireCommunityForUpdate(communityId);
    permissions.require(callerId, communityId, Permission.BAN_MEMBERS);
    Optional<CommunityBan> locked = bans.findByCommunityIdAndUserIdForUpdate(communityId, targetUserId);
    if (locked.isEmpty()) {
      return;
    }
    bans.delete(locked.get());
    audit.record(communityId, callerId, "UNBAN", targetUserId, locked.get().getId(), null);
  }

  @Transactional(readOnly = true)
  public List<BanResponse> listBans(UUID callerId, UUID communityId) {
    requireActiveUser(callerId);
    requireCommunity(communityId);
    permissions.require(callerId, communityId, Permission.BAN_MEMBERS);
    Instant now = Instant.now();
    return bans.findByCommunityIdOrderByCreatedAtDescIdDesc(communityId).stream()
        .filter(ban -> !ban.isExpired(now))
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public boolean isBanned(UUID communityId, UUID userId) {
    return bans
        .findByCommunityIdAndUserId(communityId, userId)
        .filter(ban -> !ban.isExpired(Instant.now()))
        .isPresent();
  }

  public void requireNotBanned(UUID communityId, UUID userId) {
    if (isBanned(communityId, userId)) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(), ErrorCodes.USER_BANNED, "You are banned from this community.");
    }
  }

  private void requireHierarchyAllows(UUID callerId, Community community, UUID targetUserId) {
    if (community.getOwnerId().equals(callerId)) {
      return;
    }
    int actorPosition = permissions.highestRolePosition(callerId, community.getId());
    int targetPosition = permissions.highestRolePosition(targetUserId, community.getId());
    if (targetPosition >= actorPosition) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.INSUFFICIENT_PERMISSION,
          "You cannot moderate a member at or above your highest role.");
    }
  }

  private CommunityMember requireMembership(UUID userId, UUID communityId) {
    return members
        .findByCommunityIdAndUserId(communityId, userId)
        .filter(member -> member.getStatus() == com.relayra.community.domain.MemberStatus.ACTIVE)
        .orElseThrow(
            () ->
                new DomainException(
                    HttpStatus.NOT_FOUND.value(),
                    ErrorCodes.RESOURCE_NOT_FOUND,
                    "Member was not found."));
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

  private Community requireCommunityForUpdate(UUID communityId) {
    return communities
        .findByIdForUpdate(communityId)
        .orElseThrow(
            () ->
                new DomainException(
                    HttpStatus.NOT_FOUND.value(),
                    ErrorCodes.RESOURCE_NOT_FOUND,
                    "Community was not found."));
  }

  private User requireActiveUser(UUID userId) {
    User user =
        users
            .findById(userId)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "User was not found."));
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(), ErrorCodes.ACCESS_DENIED, "Account is disabled.");
    }
    return user;
  }

  private String normalizeReason(String reason) {
    if (reason == null) {
      return null;
    }
    String trimmed = reason.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private Instant normalizeExpiry(Instant expiresAt) {
    if (expiresAt == null) {
      return null;
    }
    if (!expiresAt.isAfter(Instant.now())) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "expiresAt must be in the future.");
    }
    return expiresAt;
  }

  private void checkMutationRate(UUID callerId) {
    rateLimiter.check(
        RateLimitedException.deviceKey("moderation-mutate", callerId), 60, Duration.ofMinutes(1));
  }

  private BanResponse toResponse(CommunityBan ban) {
    return new BanResponse(
        ban.getId(),
        ban.getCommunityId(),
        ban.getUserId(),
        ban.getBannedBy(),
        ban.getReason(),
        ban.getExpiresAt(),
        ban.getCreatedAt());
  }
}
