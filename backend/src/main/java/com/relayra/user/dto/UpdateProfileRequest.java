package com.relayra.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 64, message = "Display name must be at most 64 characters.") String displayName,
    @Size(max = 500, message = "Bio must be at most 500 characters.") String bio,
    @Size(max = 512, message = "Avatar key must be at most 512 characters.") String avatarKey,
    @Size(max = 512, message = "Banner key must be at most 512 characters.") String bannerKey,
    @Size(max = 64, message = "Timezone must be at most 64 characters.") String timezone) {}
