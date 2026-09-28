package com.relayra.invite.web;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.community.dto.MemberResponse;
import com.relayra.invite.InviteService;
import com.relayra.invite.dto.CreateInviteRequest;
import com.relayra.invite.dto.InviteResponse;
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
public class InviteController {

  private final InviteService invites;

  public InviteController(InviteService invites) {
    this.invites = invites;
  }

  @PostMapping("/communities/{communityId}/invites")
  public ResponseEntity<InviteResponse> create(
      @PathVariable UUID communityId,
      @Valid @RequestBody(required = false) CreateInviteRequest request,
      Authentication authentication) {
    InviteResponse created =
        invites.create(
            requireCaller(authentication),
            communityId,
            request == null ? new CreateInviteRequest(null, null) : request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/communities/{communityId}/invites")
  public ResponseEntity<List<InviteResponse>> list(
      @PathVariable UUID communityId, Authentication authentication) {
    return ResponseEntity.ok(invites.listForCommunity(requireCaller(authentication), communityId));
  }

  @GetMapping("/invites/{code}")
  public ResponseEntity<InviteResponse> resolve(
      @PathVariable String code, Authentication authentication) {
    return ResponseEntity.ok(invites.resolve(requireCaller(authentication), code));
  }

  @PostMapping("/invites/{code}/join")
  public ResponseEntity<MemberResponse> join(
      @PathVariable String code, Authentication authentication) {
    return ResponseEntity.ok(invites.join(requireCaller(authentication), code));
  }

  @DeleteMapping("/invites/{inviteId}")
  public ResponseEntity<Void> revoke(
      @PathVariable UUID inviteId, Authentication authentication) {
    invites.revoke(requireCaller(authentication), inviteId);
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
