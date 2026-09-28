package com.relayra.friendship;

import com.relayra.auth.RateLimiter;
import com.relayra.auth.RateLimitedException;
import com.relayra.auth.domain.Profile;
import com.relayra.auth.domain.User;
import com.relayra.auth.domain.UserStatus;
import com.relayra.auth.persistence.ProfileRepository;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.friendship.domain.Friendship;
import com.relayra.friendship.domain.FriendshipStatus;
import com.relayra.friendship.domain.UserBlock;
import com.relayra.friendship.dto.BlockedUserResponse;
import com.relayra.friendship.dto.FriendResponse;
import com.relayra.friendship.dto.FriendshipResponse;
import com.relayra.friendship.persistence.FriendshipRepository;
import com.relayra.friendship.persistence.UserBlockRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendshipService {

  private final UserRepository users;
  private final ProfileRepository profiles;
  private final FriendshipRepository friendships;
  private final UserBlockRepository blocks;
  private final RateLimiter rateLimiter;
  private final ApplicationEventPublisher eventPublisher;

  public FriendshipService(
      UserRepository users,
      ProfileRepository profiles,
      FriendshipRepository friendships,
      UserBlockRepository blocks,
      RateLimiter rateLimiter,
      ApplicationEventPublisher eventPublisher) {
    this.users = users;
    this.profiles = profiles;
    this.friendships = friendships;
    this.blocks = blocks;
    this.rateLimiter = rateLimiter;
    this.eventPublisher = eventPublisher;
  }

  @Transactional(readOnly = true)
  public List<FriendResponse> listFriends(UUID callerId) {
    requireActive(callerId);
    List<Friendship> accepted = friendships.findAcceptedForUser(callerId);
    List<UUID> friendIds =
        accepted.stream()
            .map(f -> f.getSenderId().equals(callerId) ? f.getReceiverId() : f.getSenderId())
            .distinct()
            .toList();
    Map<UUID, User> usersById =
        users.findAllById(friendIds).stream().collect(Collectors.toMap(User::getId, u -> u));
    Map<UUID, String> displayNames =
        profiles.findByUserIdIn(friendIds).stream()
            .collect(Collectors.toMap(Profile::getUserId, Profile::getDisplayName));
    return accepted.stream()
        .map(
            f -> {
              UUID friendId =
                  f.getSenderId().equals(callerId) ? f.getReceiverId() : f.getSenderId();
              User friend = usersById.get(friendId);
              String username = friend == null ? "unknown" : friend.getUsername();
              String displayName = displayNames.getOrDefault(friendId, username);
              return new FriendResponse(friendId, username, displayName, f.getUpdatedAt());
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FriendshipResponse> incomingRequests(UUID callerId) {
    requireActive(callerId);
    return friendships.findByReceiverIdAndStatus(callerId, FriendshipStatus.PENDING).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FriendshipResponse> outgoingRequests(UUID callerId) {
    requireActive(callerId);
    return friendships.findBySenderIdAndStatus(callerId, FriendshipStatus.PENDING).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public FriendshipResponse sendRequest(UUID callerId, UUID receiverId) {
    if (callerId.equals(receiverId)) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "You cannot send a friend request to yourself.");
    }
    User caller = requireActive(callerId);
    User receiver = requireTarget(receiverId);
    lockPairInOrder(caller.getId(), receiver.getId());
    rateLimiter.check(
        RateLimitedException.deviceKey("friend-request", callerId), 20, Duration.ofHours(1));
    if (blockedEitherWay(callerId, receiverId)) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(), ErrorCodes.USER_BLOCKED, "Friend request is not allowed.");
    }
    UUID low = callerId.compareTo(receiverId) < 0 ? callerId : receiverId;
    UUID high = callerId.compareTo(receiverId) < 0 ? receiverId : callerId;
    friendships.findPendingBetween(low, high).ifPresent(f -> {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.DUPLICATE_RESOURCE,
          "A pending request already exists between these users.");
    });
    if (friendships.findAcceptedBetween(caller.getId(), receiver.getId()).isPresent()) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.DUPLICATE_RESOURCE,
          "You are already friends with this user.");
    }
    try {
      Friendship saved = friendships.saveAndFlush(new Friendship(UUID.randomUUID(), caller.getId(), receiver.getId()));
      if (blockedEitherWay(caller.getId(), receiver.getId())) {
        throw new DomainException(
            HttpStatus.FORBIDDEN.value(),
            ErrorCodes.USER_BLOCKED,
            "Friend request is not allowed.");
      }
      eventPublisher.publishEvent(
          new FriendRequestEvent(saved.getId(), caller.getId(), receiver.getId()));
      return toResponse(saved);
    } catch (DataIntegrityViolationException e) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.DUPLICATE_RESOURCE,
          "A pending request already exists between these users.");
    }
  }

  @Transactional
  public FriendshipResponse accept(UUID callerId, UUID requestId) {
    requireActive(callerId);
    Friendship request = requirePendingRequest(requestId);
    if (!request.getReceiverId().equals(callerId)) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.ACCESS_DENIED,
          "Only the receiver can accept this request.");
    }
    request.accept();
    return toResponse(request);
  }

  @Transactional
  public void reject(UUID callerId, UUID requestId) {
    requireActive(callerId);
    Friendship request = requirePendingRequest(requestId);
    if (!request.getReceiverId().equals(callerId)) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.ACCESS_DENIED,
          "Only the receiver can reject this request.");
    }
    request.reject();
  }

  @Transactional
  public void cancel(UUID callerId, UUID requestId) {
    requireActive(callerId);
    Friendship request = requirePendingRequest(requestId);
    if (!request.getSenderId().equals(callerId)) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.ACCESS_DENIED,
          "Only the sender can cancel this request.");
    }
    request.cancel();
  }

  @Transactional
  public void removeFriend(UUID callerId, UUID friendId) {
    requireActive(callerId);
    lockPairInOrder(callerId, friendId);
    Friendship friendship =
        friendships
            .findAcceptedBetween(callerId, friendId)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "Friendship was not found."));
    friendships.delete(friendship);
  }

  @Transactional
  public void block(UUID callerId, UUID targetId) {
    if (callerId.equals(targetId)) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "You cannot block yourself.");
    }
    requireActive(callerId);
    requireTarget(targetId);
    lockPairInOrder(callerId, targetId);
    if (!blocks.existsByBlockerIdAndBlockedId(callerId, targetId)) {
      try {
        blocks.saveAndFlush(new UserBlock(UUID.randomUUID(), callerId, targetId));
      } catch (DataIntegrityViolationException e) {
        // Concurrent block: already blocked, continue with cleanup.
      }
    }
    friendships.findAcceptedBetween(callerId, targetId).ifPresent(friendships::delete);
    friendships
        .findPendingBetween(
            callerId.compareTo(targetId) < 0 ? callerId : targetId,
            callerId.compareTo(targetId) < 0 ? targetId : callerId)
        .ifPresent(friendships::delete);
  }

  @Transactional
  public void unblock(UUID callerId, UUID targetId) {
    requireActive(callerId);
    blocks.deleteByBlockerIdAndBlockedId(callerId, targetId);
  }

  @Transactional(readOnly = true)
  public List<BlockedUserResponse> blockedUsers(UUID callerId) {
    requireActive(callerId);
    List<UserBlock> blocked = blocks.findByBlockerId(callerId);
    List<UUID> ids = blocked.stream().map(UserBlock::getBlockedId).distinct().toList();
    Map<UUID, String> usernames =
        users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getUsername));
    return blocked.stream()
        .map(b -> new BlockedUserResponse(
            b.getBlockedId(), usernames.getOrDefault(b.getBlockedId(), "unknown"), b.getCreatedAt()))
        .toList();
  }

  private User requireActive(UUID userId) {
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

  private User requireTarget(UUID userId) {
    return users
        .findById(userId)
        .filter(u -> u.getStatus() == UserStatus.ACTIVE)
        .orElseThrow(
            () ->
                new DomainException(
                    HttpStatus.NOT_FOUND.value(),
                    ErrorCodes.RESOURCE_NOT_FOUND,
                    "User was not found."));
  }

  private void lockPairInOrder(UUID a, UUID b) {
    UUID first = a.compareTo(b) < 0 ? a : b;
    UUID second = a.compareTo(b) < 0 ? b : a;
    users.findByIdForUpdate(first).orElseThrow(
        () ->
            new DomainException(
                HttpStatus.NOT_FOUND.value(),
                ErrorCodes.RESOURCE_NOT_FOUND,
                "User was not found."));
    users.findByIdForUpdate(second).orElseThrow(
        () ->
            new DomainException(
                HttpStatus.NOT_FOUND.value(),
                ErrorCodes.RESOURCE_NOT_FOUND,
                "User was not found."));
  }

  private Friendship requirePendingRequest(UUID requestId) {
    Friendship request =
        friendships
            .findById(requestId)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "Friend request was not found."));
    if (request.getStatus() != FriendshipStatus.PENDING) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.CONFLICT,
          "Friend request is no longer pending.");
    }
    return request;
  }

  private boolean blockedEitherWay(UUID a, UUID b) {
    return blocks.existsByBlockerIdAndBlockedId(a, b)
        || blocks.existsByBlockerIdAndBlockedId(b, a);
  }

  private FriendshipResponse toResponse(Friendship f) {
    return new FriendshipResponse(
        f.getId(), f.getSenderId(), f.getReceiverId(), f.getStatus(), f.getCreatedAt(), f.getUpdatedAt());
  }
}
