package com.relayra.realtime.web;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.friendship.FriendshipService;
import com.relayra.friendship.dto.FriendResponse;
import com.relayra.realtime.PresenceService;
import com.relayra.realtime.dto.PresenceResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presence")
public class PresenceController {

  private final PresenceService presence;
  private final FriendshipService friendships;

  public PresenceController(PresenceService presence, FriendshipService friendships) {
    this.presence = presence;
    this.friendships = friendships;
  }

  @GetMapping
  public ResponseEntity<List<PresenceResponse>> friends(Authentication authentication) {
    UUID callerId = requireCaller(authentication);
    List<FriendResponse> friends = friendships.listFriends(callerId);
    Map<UUID, String> statuses =
        presence.statusesFor(
            friends.stream().map(FriendResponse::userId).collect(Collectors.toSet()));
    return ResponseEntity.ok(
        friends.stream()
            .map(
                friend ->
                    new PresenceResponse(
                        friend.userId(),
                        friend.username(),
                        friend.displayName(),
                        statuses.getOrDefault(friend.userId(), "OFFLINE")))
            .toList());
  }

  @GetMapping("/status")
  public ResponseEntity<Map<UUID, String>> statuses(
      @RequestParam List<UUID> userIds, Authentication authentication) {
    UUID callerId = requireCaller(authentication);
    if (userIds.size() > 100) {
      throw new DomainException(
          400, ErrorCodes.VALIDATION_FAILED, "At most 100 users can be queried at once.");
    }
    Set<UUID> friends =
        friendships.listFriends(callerId).stream()
            .map(FriendResponse::userId)
            .collect(Collectors.toSet());
    Set<UUID> requested =
        userIds.stream()
            .distinct()
            .filter(friends::contains)
            .limit(100)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    return ResponseEntity.ok(presence.statusesFor(requested));
  }

  private UUID requireCaller(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
    return userId;
  }
}
