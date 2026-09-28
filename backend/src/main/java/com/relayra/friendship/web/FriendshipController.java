package com.relayra.friendship.web;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.friendship.FriendshipService;
import com.relayra.friendship.dto.BlockedUserResponse;
import com.relayra.friendship.dto.FriendResponse;
import com.relayra.friendship.dto.FriendshipResponse;
import com.relayra.friendship.dto.SendFriendRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FriendshipController {

  private final FriendshipService friendships;

  public FriendshipController(FriendshipService friendships) {
    this.friendships = friendships;
  }

  @GetMapping("/friends")
  public ResponseEntity<List<FriendResponse>> listFriends(Authentication authentication) {
    return ResponseEntity.ok(friendships.listFriends(requireCaller(authentication)));
  }

  @GetMapping("/friends/requests/incoming")
  public ResponseEntity<List<FriendshipResponse>> incoming(Authentication authentication) {
    return ResponseEntity.ok(friendships.incomingRequests(requireCaller(authentication)));
  }

  @GetMapping("/friends/requests/outgoing")
  public ResponseEntity<List<FriendshipResponse>> outgoing(Authentication authentication) {
    return ResponseEntity.ok(friendships.outgoingRequests(requireCaller(authentication)));
  }

  @PostMapping("/friends/requests")
  public ResponseEntity<FriendshipResponse> send(
      @Valid @RequestBody SendFriendRequest request, Authentication authentication) {
    FriendshipResponse created =
        friendships.sendRequest(requireCaller(authentication), request.receiverId());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @DeleteMapping("/friends/requests/{requestId}")
  public ResponseEntity<Void> cancel(
      @PathVariable UUID requestId, Authentication authentication) {
    friendships.cancel(requireCaller(authentication), requestId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/friends/requests/{requestId}/accept")
  public ResponseEntity<FriendshipResponse> accept(
      @PathVariable UUID requestId, Authentication authentication) {
    return ResponseEntity.ok(friendships.accept(requireCaller(authentication), requestId));
  }

  @PostMapping("/friends/requests/{requestId}/reject")
  public ResponseEntity<Void> reject(
      @PathVariable UUID requestId, Authentication authentication) {
    friendships.reject(requireCaller(authentication), requestId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/friends/{userId}")
  public ResponseEntity<Void> remove(
      @PathVariable UUID userId, Authentication authentication) {
    friendships.removeFriend(requireCaller(authentication), userId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/friends/blocked")
  public ResponseEntity<List<BlockedUserResponse>> blocked(Authentication authentication) {
    return ResponseEntity.ok(friendships.blockedUsers(requireCaller(authentication)));
  }

  @PostMapping("/users/{userId}/block")
  public ResponseEntity<Void> block(@PathVariable UUID userId, Authentication authentication) {
    friendships.block(requireCaller(authentication), userId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/users/{userId}/block")
  public ResponseEntity<Void> unblock(@PathVariable UUID userId, Authentication authentication) {
    friendships.unblock(requireCaller(authentication), userId);
    return ResponseEntity.noContent().build();
  }

  private UUID requireCaller(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
    return userId;
  }
}
