package com.relayra.role.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReorderRolesRequest(
    @NotNull(message = "roleIds is required.")
        List<@NotNull(message = "roleIds must not contain null values.") UUID> roleIds) {}
