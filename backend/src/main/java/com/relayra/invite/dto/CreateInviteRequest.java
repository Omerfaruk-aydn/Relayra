package com.relayra.invite.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import java.time.Instant;

public record CreateInviteRequest(
    @Min(value = 1, message = "maxUses must be positive.") Integer maxUses,
    @Future(message = "expiresAt must be in the future.") Instant expiresAt) {}
