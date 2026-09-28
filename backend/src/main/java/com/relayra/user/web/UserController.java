package com.relayra.user.web;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.user.UserService;
import com.relayra.user.dto.PatchProfileRequest;
import com.relayra.user.dto.PublicUserProfileResponse;
import com.relayra.user.dto.UserProfileResponse;
import com.relayra.user.dto.UserSearchResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/{userId}")
  public ResponseEntity<PublicUserProfileResponse> getUser(
      @PathVariable UUID userId, Authentication authentication) {
    requireCaller(authentication);
    return ResponseEntity.ok(userService.getPublicProfile(userId));
  }

  @GetMapping("/search")
  public ResponseEntity<List<UserSearchResult>> search(
      @RequestParam("q") String query,
      @RequestParam(value = "limit", required = false) Integer limit,
      Authentication authentication) {
    UUID callerId = requireCaller(authentication);
    return ResponseEntity.ok(userService.search(callerId, query, limit));
  }

  @PatchMapping("/me/profile")
  public ResponseEntity<UserProfileResponse> updateMyProfile(
      @RequestBody Map<String, String> fields, Authentication authentication) {
    UUID callerId = requireCaller(authentication);
    PatchProfileRequest request =
        new PatchProfileRequest(
            field(fields, "displayName"),
            field(fields, "bio"),
            field(fields, "avatarKey"),
            field(fields, "bannerKey"),
            field(fields, "timezone"));
    return ResponseEntity.ok(userService.updateMyProfile(callerId, request));
  }

  private Optional<String> field(Map<String, String> fields, String name) {
    if (fields == null || !fields.containsKey(name)) {
      return Optional.empty();
    }
    return Optional.ofNullable(fields.get(name));
  }

  private UUID requireCaller(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
    return userId;
  }
}
