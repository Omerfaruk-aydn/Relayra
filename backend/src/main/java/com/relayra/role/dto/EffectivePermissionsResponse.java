package com.relayra.role.dto;

import com.relayra.role.domain.Permission;
import java.util.Set;
import java.util.UUID;

public record EffectivePermissionsResponse(
    UUID communityId, UUID userId, Set<Permission> permissions) {}
