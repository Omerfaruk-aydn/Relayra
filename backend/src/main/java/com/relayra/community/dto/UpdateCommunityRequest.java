package com.relayra.community.dto;

import jakarta.validation.constraints.Size;

public record UpdateCommunityRequest(
    @Size(min = 2, max = 100, message = "Community name must be between 2 and 100 characters.")
        String name,
    @Size(max = 1000, message = "Description must be at most 1000 characters.")
        String description,
    @Size(max = 512, message = "Icon key must be at most 512 characters.") String iconKey) {}
