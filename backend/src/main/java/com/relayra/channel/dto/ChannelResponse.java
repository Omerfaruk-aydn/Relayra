package com.relayra.channel.dto;

import com.relayra.channel.domain.ChannelType;
import java.time.Instant;
import java.util.UUID;

public record ChannelResponse(
    UUID id,
    UUID communityId,
    String name,
    String description,
    ChannelType type,
    int position,
    Instant createdAt,
    Instant updatedAt) {}
