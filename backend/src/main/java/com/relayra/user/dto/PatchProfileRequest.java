package com.relayra.user.dto;

import java.util.Optional;

public record PatchProfileRequest(
    Optional<String> displayName,
    Optional<String> bio,
    Optional<String> avatarKey,
    Optional<String> bannerKey,
    Optional<String> timezone) {}
