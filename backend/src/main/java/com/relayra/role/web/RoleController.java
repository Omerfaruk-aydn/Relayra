package com.relayra.role.web;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.role.PermissionService;
import com.relayra.role.RoleService;
import com.relayra.role.dto.AssignRoleRequest;
import com.relayra.role.dto.CreateRoleRequest;
import com.relayra.role.dto.EffectivePermissionsResponse;
import com.relayra.role.dto.ReorderRolesRequest;
import com.relayra.role.dto.RoleResponse;
import com.relayra.role.dto.UpdateRoleRequest;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1")
public class RoleController {

  private final RoleService roles;
  private final PermissionService permissions;

  public RoleController(RoleService roles, PermissionService permissions) {
    this.roles = roles;
    this.permissions = permissions;
  }

  @PostMapping("/communities/{communityId}/roles")
  public ResponseEntity<RoleResponse> create(
      @PathVariable UUID communityId,
      @Valid @RequestBody CreateRoleRequest request,
      Authentication authentication) {
    RoleResponse created = roles.create(requireCaller(authentication), communityId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/communities/{communityId}/roles")
  public ResponseEntity<List<RoleResponse>> list(
      @PathVariable UUID communityId, Authentication authentication) {
    return ResponseEntity.ok(roles.list(requireCaller(authentication), communityId));
  }

  @PatchMapping("/roles/{roleId}")
  public ResponseEntity<RoleResponse> update(
      @PathVariable UUID roleId,
      @Valid @RequestBody UpdateRoleRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(roles.update(requireCaller(authentication), roleId, request));
  }

  @DeleteMapping("/roles/{roleId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID roleId, Authentication authentication) {
    roles.delete(requireCaller(authentication), roleId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/communities/{communityId}/roles/reorder")
  public ResponseEntity<List<RoleResponse>> reorder(
      @PathVariable UUID communityId,
      @Valid @RequestBody ReorderRolesRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(roles.reorder(requireCaller(authentication), communityId, request));
  }

  @PostMapping("/communities/{communityId}/members/{userId}/roles")
  public ResponseEntity<RoleResponse> assign(
      @PathVariable UUID communityId,
      @PathVariable UUID userId,
      @Valid @RequestBody AssignRoleRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        roles.assign(requireCaller(authentication), communityId, userId, request.roleId()));
  }

  @DeleteMapping("/communities/{communityId}/members/{userId}/roles/{roleId}")
  public ResponseEntity<Void> remove(
      @PathVariable UUID communityId,
      @PathVariable UUID userId,
      @PathVariable UUID roleId,
      Authentication authentication) {
    roles.remove(requireCaller(authentication), communityId, userId, roleId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/communities/{communityId}/members/{userId}/roles")
  public ResponseEntity<List<RoleResponse>> listMemberRoles(
      @PathVariable UUID communityId,
      @PathVariable UUID userId,
      Authentication authentication) {
    return ResponseEntity.ok(
        roles.listMemberRoles(requireCaller(authentication), communityId, userId));
  }

  @GetMapping("/communities/{communityId}/permissions/me")
  public ResponseEntity<EffectivePermissionsResponse> effective(
      @PathVariable UUID communityId, Authentication authentication) {
    UUID callerId = requireCaller(authentication);
    return ResponseEntity.ok(
        new EffectivePermissionsResponse(
            communityId, callerId, permissions.resolve(callerId, communityId)));
  }

  private UUID requireCaller(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
    return userId;
  }
}
