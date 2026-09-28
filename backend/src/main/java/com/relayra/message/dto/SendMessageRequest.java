package com.relayra.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendMessageRequest(
    @NotBlank(message = "clientMessageId is required.")
        @Size(max = 128, message = "clientMessageId must be at most 128 characters.")
        String clientMessageId,
    @NotBlank(message = "Message content is required.")
        @Size(max = 4000, message = "Message content must be at most 4000 characters.")
        String content,
    UUID replyToMessageId) {}
