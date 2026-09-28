package com.relayra.community.web;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.community.CommunityService;
import com.relayra.community.dto.CommunityResponse;
import com.relayra.community.dto.CreateCommunityRequest;
import com.relayra.community.dto.MemberResponse;
import com.relayra.community.dto.TransferOwnershipRequest;
import com.relayra.community.dto.UpdateCommunityRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/communities")
public class CommunityController {

  private final CommunityService communities;

  public CommunityController(CommunityService communities) {
    this.communities = communities;
  }

  @PostMapping
  public ResponseEntity<CommunityResponse> create(
      @Valid @RequestBody CreateCommunityRequest request, Authentication authentication) {
    CommunityResponse created = communities.create(requireCaller(authentication), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping
  public ResponseEntity<List<CommunityResponse>> listMine(Authentication authentication) {
    return ResponseEntity.ok(communities.listMine(requireCaller(authentication)));
  }

  @GetMapping("/{communityId}")
  public ResponseEntity<CommunityResponse> get(
      @PathVariable UUID communityId, Authentication authentication) {
    return ResponseEntity.ok(communities.get(requireCaller(authentication), communityId));
  }

  @PatchMapping("/{communityId}")
  public ResponseEntity<CommunityResponse> update(
      @PathVariable UUID communityId,
      @Valid @RequestBody UpdateCommunityRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(communities.update(requireCaller(authentication), communityId, request));
  }

  @DeleteMapping("/{communityId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID communityId,
      @RequestBody(required = false) Map<String, String> body,
      Authentication authentication) {
    String confirmName = body == null ? null : body.get("confirmName");
    communities.delete(requireCaller(authentication), communityId, confirmName);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{communityId}/leave")
  public ResponseEntity<Void> leave(
      @PathVariable UUID communityId, Authentication authentication) {
    communities.leave(requireCaller(authentication), communityId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{communityId}/transfer-ownership")
  public ResponseEntity<CommunityResponse> transfer(
      @PathVariable UUID communityId,
      @Valid @RequestBody TransferOwnershipRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        communities.transferOwnership(requireCaller(authentication), communityId, request));
  }

  @GetMapping("/{communityId}/members")
  public ResponseEntity<List<MemberResponse>> members(
      @PathVariable UUID communityId, Authentication authentication) {
    return ResponseEntity.ok(
        communities.listMembers(requireCaller(authentication), communityId));
  }

  private UUID requireCaller(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
    return userId;
  }
}
