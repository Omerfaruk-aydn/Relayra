package com.relayra.attachment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record AttachRequest(@NotNull(message = "attachmentIds is required.") List<UUID> attachmentIds) {}
