package com.relayra.channel.dto;

import jakarta.validation.constraints.Size;

public record UpdateChannelRequest(
    @Size(max = 100, message = "Channel name must be at most 100 characters.") String name,
    @Size(max = 250, message = "Description must be at most 250 characters.")
        String description) {}
