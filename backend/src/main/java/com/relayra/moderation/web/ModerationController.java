package com.relayra.moderation.web;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.moderation.ModerationService;
import com.relayra.moderation.dto.BanRequest;
import com.relayra.moderation.dto.BanResponse;
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
@RequestMapping("/api/v1/communities/{communityId}")
public class ModerationController {

  private final ModerationService moderation;

  public ModerationController(ModerationService moderation) {
    this.moderation = moderation;
  }

  @DeleteMapping("/members/{userId}")
  public ResponseEntity<Void> kick(
      @PathVariable UUID communityId,
      @PathVariable UUID userId,
      Authentication authentication) {
    moderation.kick(requireCaller(authentication), communityId, userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/bans")
  public ResponseEntity<BanResponse> ban(
      @PathVariable UUID communityId,
      @Valid @RequestBody BanRequest request,
      Authentication authentication) {
    BanResponse banned = moderation.ban(requireCaller(authentication), communityId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(banned);
  }

  @GetMapping("/bans")
  public ResponseEntity<List<BanResponse>> listBans(
      @PathVariable UUID communityId, Authentication authentication) {
    return ResponseEntity.ok(moderation.listBans(requireCaller(authentication), communityId));
  }

  @DeleteMapping("/bans/{userId}")
  public ResponseEntity<Void> unban(
      @PathVariable UUID communityId,
      @PathVariable UUID userId,
      Authentication authentication) {
    moderation.unban(requireCaller(authentication), communityId, userId);
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
