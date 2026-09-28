package com.relayra.role.dto;

import com.relayra.role.domain.Permission;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RoleResponse(
    UUID id,
    UUID communityId,
    String name,
    int position,
    String color,
    boolean managed,
    Set<Permission> permissions,
    Instant createdAt,
    Instant updatedAt) {}
