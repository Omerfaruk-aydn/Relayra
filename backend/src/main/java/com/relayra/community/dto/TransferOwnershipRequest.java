package com.relayra.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TransferOwnershipRequest(
    @NotNull(message = "New owner id is required.") UUID newOwnerId,
    @NotBlank(message = "Confirmation name is required.") String confirmName) {}
