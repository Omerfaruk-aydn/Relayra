package com.relayra.audit.web;

import com.relayra.audit.AuditService;
import com.relayra.audit.dto.AuditPageResponse;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.role.PermissionService;
import com.relayra.role.domain.Permission;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/communities/{communityId}")
public class AuditController {

  private final AuditService audit;
  private final PermissionService permissions;

  public AuditController(AuditService audit, PermissionService permissions) {
    this.audit = audit;
    this.permissions = permissions;
  }

  @GetMapping("/audit-log")
  public ResponseEntity<AuditPageResponse> history(
      @PathVariable UUID communityId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Instant beforeCreatedAt,
      @RequestParam(required = false) UUID beforeId,
      Authentication authentication) {
    UUID callerId = requireCaller(authentication);
    permissions.require(callerId, communityId, Permission.VIEW_AUDIT_LOG);
    return ResponseEntity.ok(audit.history(communityId, limit, beforeCreatedAt, beforeId));
  }

  private UUID requireCaller(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
    return userId;
  }
}
