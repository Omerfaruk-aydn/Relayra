package com.relayra.attachment.dto;

import java.time.Instant;
import java.util.UUID;

public record UploadResponse(
    UUID id, String originalFilename, String mimeType, long sizeBytes, Instant createdAt) {}
