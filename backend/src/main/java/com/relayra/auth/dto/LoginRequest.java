package com.relayra.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "Identifier is required.")
        @Size(max = 320, message = "Identifier must be at most 320 characters.")
        String identifier,
    @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must contain between 8 and 72 characters.")
        String password) {}
