package com.relayra.role.dto;

import com.relayra.role.domain.Permission;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateRoleRequest(
    @Size(max = 64, message = "Role name must be at most 64 characters.") String name,
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Role color must be a hex color.")
        String color,
    Set<Permission> permissions) {}
