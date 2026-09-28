package com.relayra.community;

import com.relayra.auth.domain.User;
import com.relayra.auth.domain.UserStatus;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.channel.domain.Channel;
import com.relayra.channel.domain.ChannelType;
import com.relayra.channel.persistence.ChannelRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.community.domain.Community;
import com.relayra.community.domain.CommunityMember;
import com.relayra.community.domain.MemberStatus;
import com.relayra.community.dto.CommunityResponse;
import com.relayra.community.dto.CreateCommunityRequest;
import com.relayra.community.dto.MemberResponse;
import com.relayra.community.dto.TransferOwnershipRequest;
import com.relayra.community.dto.UpdateCommunityRequest;
import com.relayra.community.persistence.CommunityMemberRepository;
import com.relayra.community.persistence.CommunityRepository;
import com.relayra.role.domain.Permission;
import com.relayra.role.domain.Role;
import com.relayra.role.persistence.RoleRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityService {

  private final CommunityRepository communities;
  private final CommunityMemberRepository members;
  private final ChannelRepository channels;
  private final RoleRepository roles;
  private final UserRepository users;

  public CommunityService(
      CommunityRepository communities,
      CommunityMemberRepository members,
      ChannelRepository channels,
      RoleRepository roles,
      UserRepository users) {
    this.communities = communities;
    this.members = members;
    this.channels = channels;
    this.roles = roles;
    this.users = users;
  }

  @Transactional
  public CommunityResponse create(UUID callerId, CreateCommunityRequest request) {
    requireActiveUser(callerId);
    String name = request.name().trim();
    if (name.length() < 2 || name.length() > 100) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Community name must be between 2 and 100 characters.");
    }
    UUID communityId = UUID.randomUUID();
    Community community = new Community(communityId, callerId, name);
    community.rename(name, trimToNull(request.description()), trimToNull(request.iconKey()));
    communities.save(community);
    members.save(new CommunityMember(UUID.randomUUID(), communityId, callerId));
    channels.save(
        new Channel(UUID.randomUUID(), communityId, "general", ChannelType.TEXT, 0));
    Role everyone = new Role(UUID.randomUUID(), communityId, "@everyone", 0, true);
    everyone.update(
        "@everyone",
        null,
        EnumSet.of(
            Permission.VIEW_CHANNEL,
            Permission.SEND_MESSAGES,
            Permission.ADD_REACTIONS,
            Permission.ATTACH_FILES));
    roles.save(everyone);
    return toResponse(community, 1L);
  }

  @Transactional(readOnly = true)
  public List<CommunityResponse> listMine(UUID callerId) {
    requireActiveUser(callerId);
    List<CommunityMember> memberships = members.findByUserIdAndStatus(callerId, MemberStatus.ACTIVE);
    return memberships.stream()
        .map(
            m ->
                communities
                    .findById(m.getCommunityId())
                    .map(
                        c ->
                            toResponse(
                                c,
                                members.countByCommunityIdAndStatus(
                                    c.getId(), MemberStatus.ACTIVE)))
                    .orElse(null))
        .filter(r -> r != null)
        .toList();
  }

  @Transactional(readOnly = true)
  public CommunityResponse get(UUID callerId, UUID communityId) {
    requireActiveUser(callerId);
    requireMembership(callerId, communityId);
    Community community = requireCommunity(communityId);
    return toResponse(
        community, members.countByCommunityIdAndStatus(communityId, MemberStatus.ACTIVE));
  }

  @Transactional
  public CommunityResponse update(
      UUID callerId, UUID communityId, UpdateCommunityRequest request) {
    requireActiveUser(callerId);
    Community community = requireCommunity(communityId);
    requireOwner(callerId, community);
    String name = request.name() == null ? community.getName() : request.name().trim();
    String description =
        request.description() == null ? community.getDescription() : trimToNull(request.description());
    String iconKey =
        request.iconKey() == null ? community.getIconKey() : trimToNull(request.iconKey());
    if (name.length() < 2 || name.length() > 100) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Community name must be between 2 and 100 characters.");
    }
    community.rename(name, description, iconKey);
    return toResponse(
        community, members.countByCommunityIdAndStatus(communityId, MemberStatus.ACTIVE));
  }

  @Transactional
  public void delete(UUID callerId, UUID communityId, String confirmName) {
    requireActiveUser(callerId);
    Community community = requireCommunity(communityId);
    requireOwner(callerId, community);
    if (confirmName == null || !confirmName.equals(community.getName())) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Confirmation name does not match the community name.");
    }
    channels.deleteByCommunityId(communityId);
    roles.deleteByCommunityId(communityId);
    communities.delete(community);
  }

  @Transactional
  public void leave(UUID callerId, UUID communityId) {
    requireActiveUser(callerId);
    Community community = requireCommunity(communityId);
    requireMembership(callerId, communityId);
    if (community.getOwnerId().equals(callerId)) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.CONFLICT,
          "Owner cannot leave the community. Transfer ownership or delete it first.");
    }
    members.deleteByCommunityIdAndUserId(communityId, callerId);
  }

  @Transactional
  public CommunityResponse transferOwnership(
      UUID callerId, UUID communityId, TransferOwnershipRequest request) {
    requireActiveUser(callerId);
    Community community = requireCommunity(communityId);
    requireOwner(callerId, community);
    if (request.confirmName() == null || !request.confirmName().equals(community.getName())) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Confirmation name does not match the community name.");
    }
    if (request.newOwnerId().equals(callerId)) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "You already own this community.");
    }
    User next = requireActiveUser(request.newOwnerId());
    CommunityMember membership =
        members
            .findByCommunityIdAndUserId(communityId, next.getId())
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.BAD_REQUEST.value(),
                        ErrorCodes.VALIDATION_FAILED,
                        "New owner must be a community member."));
    if (membership.getStatus() != MemberStatus.ACTIVE) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "New owner membership must be active.");
    }
    community.transferOwnership(next.getId());
    return toResponse(
        community, members.countByCommunityIdAndStatus(communityId, MemberStatus.ACTIVE));
  }

  @Transactional(readOnly = true)
  public List<MemberResponse> listMembers(UUID callerId, UUID communityId) {
    requireActiveUser(callerId);
    Community community = requireCommunity(communityId);
    requireMembership(callerId, communityId);
    List<CommunityMember> all = members.findByCommunityId(communityId);
    Map<UUID, String> usernames =
        users.findAllById(all.stream().map(CommunityMember::getUserId).toList()).stream()
            .collect(Collectors.toMap(User::getId, User::getUsername));
    return all.stream()
        .map(
            m ->
                new MemberResponse(
                    m.getUserId(),
                    usernames.getOrDefault(m.getUserId(), "unknown"),
                    m.getStatus(),
                    community.getOwnerId().equals(m.getUserId()),
                    m.getJoinedAt()))
        .toList();
  }

  @Transactional
  public MemberResponse joinDirect(UUID callerId, UUID communityId) {
    User caller = requireActiveUser(callerId);
    Community community = requireCommunity(communityId);
    Optional<CommunityMember> existing = members.findByCommunityIdAndUserId(communityId, callerId);
    if (existing.isPresent()) {
      if (existing.get().getStatus() == MemberStatus.ACTIVE) {
        throw new DomainException(
            HttpStatus.CONFLICT.value(),
            ErrorCodes.DUPLICATE_RESOURCE,
            "You are already a member of this community.");
      }
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.ACCESS_DENIED,
          "Your membership is disabled. Contact a community admin.");
    }
    try {
      CommunityMember saved =
          members.saveAndFlush(
              new CommunityMember(UUID.randomUUID(), communityId, caller.getId()));
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

  private void requireMembership(UUID callerId, UUID communityId) {
    members
        .findByCommunityIdAndUserId(communityId, callerId)
        .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
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
          "Only the community owner can perform this action.");
    }
  }

  private CommunityResponse toResponse(Community community, long memberCount) {
    return new CommunityResponse(
        community.getId(),
        community.getOwnerId(),
        community.getName(),
        community.getDescription(),
        community.getIconKey(),
        memberCount,
        community.getCreatedAt(),
        community.getUpdatedAt());
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
