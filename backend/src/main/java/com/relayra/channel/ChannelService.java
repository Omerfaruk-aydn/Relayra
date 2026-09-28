package com.relayra.channel;

import com.relayra.auth.RateLimitedException;
import com.relayra.auth.RateLimiter;
import com.relayra.auth.domain.User;
import com.relayra.auth.domain.UserStatus;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.channel.domain.Channel;
import com.relayra.channel.domain.ChannelType;
import com.relayra.channel.dto.ChannelResponse;
import com.relayra.channel.dto.CreateChannelRequest;
import com.relayra.channel.dto.ReorderChannelsRequest;
import com.relayra.channel.dto.UpdateChannelRequest;
import com.relayra.channel.persistence.ChannelRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.community.domain.Community;
import com.relayra.community.persistence.CommunityRepository;
import com.relayra.audit.AuditService;
import com.relayra.role.PermissionService;
import com.relayra.role.domain.Permission;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChannelService {

  private static final int MAX_CHANNELS_PER_COMMUNITY = 200;

  private final ChannelRepository channels;
  private final CommunityRepository communities;
  private final UserRepository users;
  private final RateLimiter rateLimiter;
  private final PermissionService permissions;
  private final AuditService audit;

  public ChannelService(
      ChannelRepository channels,
      CommunityRepository communities,
      UserRepository users,
      RateLimiter rateLimiter,
      PermissionService permissions,
      AuditService audit) {
    this.channels = channels;
    this.communities = communities;
    this.users = users;
    this.rateLimiter = rateLimiter;
    this.permissions = permissions;
    this.audit = audit;
  }

  @Transactional
  public ChannelResponse create(
      UUID callerId, UUID communityId, CreateChannelRequest request) {
    requireActiveUser(callerId);
    Community community = requireCommunity(communityId);
    permissions.require(callerId, communityId, Permission.MANAGE_CHANNELS);
    checkMutationRate(callerId);
    community = requireCommunityForUpdate(communityId);
    permissions.require(callerId, communityId, Permission.MANAGE_CHANNELS);
    String name = normalizeName(request.name());
    String description = trimToNull(request.description());
    ChannelType type = parseType(request.type());
    List<Channel> existing = channels.findByCommunityIdForUpdate(communityId);
    if (existing.size() >= MAX_CHANNELS_PER_COMMUNITY) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(), ErrorCodes.CONFLICT, "Community channel limit reached.");
    }
    int nextPosition = existing.stream().mapToInt(Channel::getPosition).max().orElse(-1) + 1;
    Channel channel =
        new Channel(UUID.randomUUID(), communityId, name, type, nextPosition);
    channel.update(name, description);
    ChannelResponse created = toResponse(channels.saveAndFlush(channel));
    audit.record(
        community.getId(), callerId, "CHANNEL_CREATED", null, created.id(), created.name());
    return created;
  }

  @Transactional(readOnly = true)
  public List<ChannelResponse> list(UUID callerId, UUID communityId) {
    requireActiveUser(callerId);
    permissions.require(callerId, communityId, Permission.VIEW_CHANNEL);
    requireCommunity(communityId);
    return channels.findByCommunityIdOrderByPositionAscIdAsc(communityId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public ChannelResponse update(
      UUID callerId, UUID channelId, UpdateChannelRequest request) {
    requireActiveUser(callerId);
    Channel snapshot = requireChannel(channelId);
    Community community = requireCommunity(snapshot.getCommunityId());
    permissions.require(callerId, community.getId(), Permission.MANAGE_CHANNELS);
    checkMutationRate(callerId);
    community = requireCommunityForUpdate(community.getId());
    permissions.require(callerId, community.getId(), Permission.MANAGE_CHANNELS);
    Channel channel = requireChannelForUpdate(channelId);
    if (!channel.getCommunityId().equals(community.getId())) {
      throw channelNotFound();
    }
    String name = request.name() == null ? channel.getName() : normalizeName(request.name());
    String description =
        request.description() == null ? channel.getDescription() : trimToNull(request.description());
    channel.update(name, description);
    ChannelResponse updated = toResponse(channels.saveAndFlush(channel));
    audit.record(
        community.getId(), callerId, "CHANNEL_UPDATED", null, updated.id(), updated.name());
    return updated;
  }

  @Transactional
  public void delete(UUID callerId, UUID channelId) {
    requireActiveUser(callerId);
    Channel snapshot = requireChannel(channelId);
    Community community = requireCommunity(snapshot.getCommunityId());
    permissions.require(callerId, community.getId(), Permission.MANAGE_CHANNELS);
    checkMutationRate(callerId);
    community = requireCommunityForUpdate(community.getId());
    permissions.require(callerId, community.getId(), Permission.MANAGE_CHANNELS);
    List<Channel> locked = channels.findByCommunityIdForUpdate(community.getId());
    Channel channel =
        locked.stream()
            .filter(candidate -> candidate.getId().equals(channelId))
            .findFirst()
            .orElseThrow(() -> channelNotFound());
    if (locked.size() == 1) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.CONFLICT,
          "A community must have at least one channel.");
    }
    channels.delete(channel);
    int position = 0;
    for (Channel remaining : locked) {
      if (!remaining.getId().equals(channelId)) {
        remaining.moveTo(position++);
      }
    }
    channels.flush();
    audit.record(
        community.getId(), callerId, "CHANNEL_DELETED", null, channelId, channel.getName());
  }

  @Transactional
  public List<ChannelResponse> reorder(
      UUID callerId, UUID communityId, ReorderChannelsRequest request) {
    requireActiveUser(callerId);
    Community community = requireCommunity(communityId);
    permissions.require(callerId, communityId, Permission.MANAGE_CHANNELS);
    checkMutationRate(callerId);
    community = requireCommunityForUpdate(communityId);
    permissions.require(callerId, communityId, Permission.MANAGE_CHANNELS);
    List<Channel> locked = channels.findByCommunityIdForUpdate(communityId);
    List<UUID> requested = request.channelIds();
    if (requested == null) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "channelIds must not be null.");
    }
    if (requested.size() != locked.size()
        || new HashSet<>(requested).size() != requested.size()
        || !new HashSet<>(requested).equals(
            locked.stream().map(Channel::getId).collect(java.util.stream.Collectors.toSet()))) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "channelIds must contain every community channel exactly once.");
    }
    java.util.Map<UUID, Channel> byId =
        locked.stream().collect(java.util.stream.Collectors.toMap(Channel::getId, channel -> channel));
    for (int position = 0; position < requested.size(); position++) {
      byId.get(requested.get(position)).moveTo(position);
    }
    channels.saveAllAndFlush(locked);
    return requested.stream().map(byId::get).map(this::toResponse).toList();
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

  private Channel requireChannel(UUID channelId) {
    return channels.findById(channelId).orElseThrow(() -> channelNotFound());
  }

  private Channel requireChannelForUpdate(UUID channelId) {
    return channels.findByIdForUpdate(channelId).orElseThrow(() -> channelNotFound());
  }

  private DomainException channelNotFound() {
    return new DomainException(
        HttpStatus.NOT_FOUND.value(), ErrorCodes.RESOURCE_NOT_FOUND, "Channel was not found.");
  }

  private void checkMutationRate(UUID callerId) {
    rateLimiter.check(
        RateLimitedException.deviceKey("channel-mutate", callerId), 60, Duration.ofMinutes(1));
  }

  private String normalizeName(String value) {
    String name = value == null ? "" : value.trim();
    if (name.length() < 2 || name.length() > 100) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Channel name must be between 2 and 100 characters.");
    }
    return name;
  }

  private ChannelType parseType(String value) {
    try {
      return ChannelType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Channel type must be TEXT.");
    }
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private ChannelResponse toResponse(Channel channel) {
    return new ChannelResponse(
        channel.getId(),
        channel.getCommunityId(),
        channel.getName(),
        channel.getDescription(),
        channel.getType(),
        channel.getPosition(),
        channel.getCreatedAt(),
        channel.getUpdatedAt());
  }
}
