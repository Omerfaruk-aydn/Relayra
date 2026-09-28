package com.relayra.role.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignRoleRequest(@NotNull(message = "roleId is required.") UUID roleId) {}
