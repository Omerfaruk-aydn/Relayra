package com.relayra.community.dto;

import com.relayra.community.domain.MemberStatus;
import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
    UUID userId,
    String username,
    MemberStatus status,
    boolean owner,
    Instant joinedAt) {}
