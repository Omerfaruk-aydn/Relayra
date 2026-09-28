package com.relayra.invite;

import com.relayra.auth.RateLimiter;
import com.relayra.auth.RateLimitedException;
import com.relayra.auth.domain.User;
import com.relayra.auth.domain.UserStatus;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.community.domain.Community;
import com.relayra.community.domain.CommunityMember;
import com.relayra.community.domain.MemberStatus;
import com.relayra.community.dto.MemberResponse;
import com.relayra.community.persistence.CommunityMemberRepository;
import com.relayra.community.persistence.CommunityRepository;
import com.relayra.invite.domain.Invite;
import com.relayra.invite.dto.CreateInviteRequest;
import com.relayra.invite.dto.InviteResponse;
import com.relayra.invite.persistence.InviteRepository;
import com.relayra.role.PermissionService;
import com.relayra.role.domain.Permission;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteService {

  private final InviteRepository invites;
  private final CommunityRepository communities;
  private final CommunityMemberRepository members;
  private final UserRepository users;
  private final RateLimiter rateLimiter;
  private final PermissionService permissions;
  private final SecureRandom random = new SecureRandom();

  public InviteService(
      InviteRepository invites,
      CommunityRepository communities,
      CommunityMemberRepository members,
      UserRepository users,
      RateLimiter rateLimiter,
      PermissionService permissions) {
    this.invites = invites;
    this.communities = communities;
    this.members = members;
    this.users = users;
    this.rateLimiter = rateLimiter;
    this.permissions = permissions;
  }

  @Transactional
  public InviteResponse create(UUID callerId, UUID communityId, CreateInviteRequest request) {
    requireActiveUser(callerId);
    Community community = requireCommunity(communityId);
    permissions.require(callerId, communityId, Permission.CREATE_INVITES);
    rateLimiter.check(
        RateLimitedException.deviceKey("invite-create", callerId), 20, Duration.ofHours(1));
    if (request.maxUses() != null && request.maxUses() <= 0) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "maxUses must be positive.");
    }
    if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "expiresAt must be in the future.");
    }
    for (int attempt = 0; attempt < 3; attempt++) {
      String code = randomCode();
      Invite invite = new Invite(UUID.randomUUID(), communityId, callerId, code);
      invite.configure(request.maxUses(), request.expiresAt());
      try {
        return toResponse(invites.saveAndFlush(invite));
      } catch (DataIntegrityViolationException e) {
        if (invites.findByCode(code).isPresent()) {
          continue;
        }
        throw e;
      }
    }
    throw new DomainException(
        500, ErrorCodes.INTERNAL_ERROR, "Could not generate a unique invite code.");
  }

  @Transactional(readOnly = true)
  public InviteResponse resolve(UUID callerId, String code) {
    requireActiveUser(callerId);
    rateLimiter.check(
        RateLimitedException.deviceKey("invite-resolve", callerId), 60, Duration.ofMinutes(1));
    Invite invite =
        invites
            .findByCode(code)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.INVITE_INVALID,
                        "Invite is invalid."));
    if (invite.getRevokedAt() != null) {
      throw new DomainException(
          HttpStatus.GONE.value(), ErrorCodes.INVITE_INVALID, "Invite was revoked.");
    }
    if (invite.getExpiresAt() != null && Instant.now().isAfter(invite.getExpiresAt())) {
      throw new DomainException(
          HttpStatus.GONE.value(), ErrorCodes.INVITE_EXPIRED, "Invite has expired.");
    }
    if (invite.getMaxUses() != null && invite.getUsageCount() >= invite.getMaxUses()) {
      throw new DomainException(
          HttpStatus.GONE.value(), ErrorCodes.INVITE_EXHAUSTED, "Invite is exhausted.");
    }
    return toResponse(invite);
  }

  @Transactional
  public MemberResponse join(UUID callerId, String code) {
    User caller = requireActiveUser(callerId);
    rateLimiter.check(
        RateLimitedException.deviceKey("invite-join", callerId), 60, Duration.ofMinutes(1));
    Invite invite =
        invites
            .findByCodeForUpdate(code)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.INVITE_INVALID,
                        "Invite is invalid."));
    Community community = requireCommunity(invite.getCommunityId());
    var existing = members.findByCommunityIdAndUserId(community.getId(), caller.getId());
    if (existing.isPresent()) {
      if (existing.get().getStatus() == MemberStatus.ACTIVE) {
        // Already a member: idempotent return, no usage consumed even if the invite died.
        return new MemberResponse(
            caller.getId(),
            caller.getUsername(),
            existing.get().getStatus(),
            community.getOwnerId().equals(caller.getId()),
            existing.get().getJoinedAt());
      }
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.ACCESS_DENIED,
          "Your membership is disabled. Contact a community admin.");
    }
    if (invite.getRevokedAt() != null) {
      throw new DomainException(
          HttpStatus.GONE.value(), ErrorCodes.INVITE_INVALID, "Invite was revoked.");
    }
    if (invite.getExpiresAt() != null && Instant.now().isAfter(invite.getExpiresAt())) {
      throw new DomainException(
          HttpStatus.GONE.value(), ErrorCodes.INVITE_EXPIRED, "Invite has expired.");
    }
    if (invite.getMaxUses() != null && invite.getUsageCount() >= invite.getMaxUses()) {
      throw new DomainException(
          HttpStatus.GONE.value(), ErrorCodes.INVITE_EXHAUSTED, "Invite is exhausted.");
    }
    try {
      invite.recordUse();
      invites.saveAndFlush(invite);
    } catch (DataIntegrityViolationException e) {
      throw new DomainException(
          HttpStatus.GONE.value(), ErrorCodes.INVITE_EXHAUSTED, "Invite is exhausted.");
    } catch (IllegalStateException e) {
      throw new DomainException(HttpStatus.GONE.value(), mapState(e), e.getMessage());
    }
    CommunityMember membership =
        new CommunityMember(UUID.randomUUID(), community.getId(), caller.getId());
    try {
      CommunityMember saved = members.saveAndFlush(membership);
      return new MemberResponse(
          caller.getId(),
          caller.getUsername(),
          saved.getStatus(),
          community.getOwnerId().equals(caller.getId()),
          saved.getJoinedAt());
    } catch (DataIntegrityViolationException e) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.DUPLICATE_RESOURCE,
          "You are already a member of this community.");
    }
  }

  @Transactional
  public void revoke(UUID callerId, UUID inviteId) {
    requireActiveUser(callerId);
    Invite invite =
        invites
            .findByIdForUpdate(inviteId)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "Invite was not found."));
    Community community = requireCommunity(invite.getCommunityId());
    permissions.require(callerId, community.getId(), Permission.MANAGE_INVITES);
    invite.revoke();
  }

  @Transactional(readOnly = true)
  public List<InviteResponse> listForCommunity(UUID callerId, UUID communityId) {
    requireActiveUser(callerId);
    Community community = requireCommunity(communityId);
    permissions.require(callerId, community.getId(), Permission.MANAGE_INVITES);
    return invites.findByCommunityId(communityId).stream().map(this::toResponse).toList();
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

  private String randomCode() {
    byte[] bytes = new byte[9];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String mapState(IllegalStateException e) {
    String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
    if (message.contains("revok")) {
      return ErrorCodes.INVITE_INVALID;
    }
    if (message.contains("expir")) {
      return ErrorCodes.INVITE_EXPIRED;
    }
    return ErrorCodes.INVITE_EXHAUSTED;
  }

  private InviteResponse toResponse(Invite invite) {
    return new InviteResponse(
        invite.getId(),
        invite.getCommunityId(),
        invite.getCode(),
        invite.getMaxUses(),
        invite.getUsageCount(),
        invite.getExpiresAt(),
        invite.getRevokedAt(),
        invite.getCreatedAt());
  }
}
