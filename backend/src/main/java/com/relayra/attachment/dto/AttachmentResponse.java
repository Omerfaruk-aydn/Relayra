package com.relayra.attachment.dto;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
    UUID id,
    UUID messageId,
    String originalFilename,
    String mimeType,
    long sizeBytes,
    String sha256,
    Instant createdAt) {}
