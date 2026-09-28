package com.relayra.channel.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReorderChannelsRequest(
    @NotEmpty(message = "channelIds must not be empty.")
        List<@NotNull(message = "channelIds must not contain null values.") UUID> channelIds) {}
