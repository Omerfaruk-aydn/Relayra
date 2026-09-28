package com.relayra.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChannelRequest(
    @NotBlank(message = "Channel name is required.")
        @Size(max = 100, message = "Channel name must be at most 100 characters.")
        String name,
    @Size(max = 250, message = "Description must be at most 250 characters.")
        String description,
    @NotBlank(message = "Channel type is required.") String type) {}
