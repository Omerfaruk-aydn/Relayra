package com.relayra.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Username is required.")
        @Size(min = 3, max = 32, message = "Username must contain between 3 and 32 characters.")
        @Pattern(
            regexp = "^[a-zA-Z0-9_.]+$",
            message = "Username may only contain letters, digits, underscore and dot.")
        String username,
    @NotBlank(message = "Email is required.")
        @Size(max = 320, message = "Email must be at most 320 characters.")
        @Email(message = "Email must be valid.")
        String email,
    @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must contain between 8 and 72 characters.")
        String password) {}
