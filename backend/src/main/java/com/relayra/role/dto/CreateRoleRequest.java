package com.relayra.role.dto;

import com.relayra.role.domain.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateRoleRequest(
    @NotBlank(message = "Role name is required.")
        @Size(max = 64, message = "Role name must be at most 64 characters.")
        String name,
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Role color must be a hex color.")
        String color,
    @NotNull(message = "permissions is required.") Set<Permission> permissions) {}
