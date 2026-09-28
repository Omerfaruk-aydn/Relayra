package com.relayra.message.web;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.message.MessageService;
import com.relayra.message.dto.EditMessageRequest;
import com.relayra.message.dto.MessagePageResponse;
import com.relayra.message.dto.MessageResponse;
import com.relayra.message.dto.SendMessageRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MessageController {

  private final MessageService messages;

  public MessageController(MessageService messages) {
    this.messages = messages;
  }

  @PostMapping("/channels/{channelId}/messages")
  public ResponseEntity<MessageResponse> send(
      @PathVariable UUID channelId,
      @Valid @RequestBody SendMessageRequest request,
      Authentication authentication) {
    MessageResponse created =
        messages.sendToChannel(requireCaller(authentication), channelId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/channels/{channelId}/messages")
  public ResponseEntity<MessagePageResponse> history(
      @PathVariable UUID channelId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Instant beforeCreatedAt,
      @RequestParam(required = false) UUID beforeId,
      Authentication authentication) {
    return ResponseEntity.ok(
        messages.history(
            requireCaller(authentication), channelId, limit, beforeCreatedAt, beforeId));
  }

  @PostMapping("/conversations/{conversationId}/messages")
  public ResponseEntity<MessageResponse> sendToConversation(
      @PathVariable UUID conversationId,
      @Valid @RequestBody SendMessageRequest request,
      Authentication authentication) {
    MessageResponse created =
        messages.sendToConversation(requireCaller(authentication), conversationId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/conversations/{conversationId}/messages")
  public ResponseEntity<MessagePageResponse> conversationHistory(
      @PathVariable UUID conversationId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Instant beforeCreatedAt,
      @RequestParam(required = false) UUID beforeId,
      Authentication authentication) {
    return ResponseEntity.ok(
        messages.conversationHistory(
            requireCaller(authentication), conversationId, limit, beforeCreatedAt, beforeId));
  }

  @PatchMapping("/messages/{messageId}")
  public ResponseEntity<MessageResponse> edit(
      @PathVariable UUID messageId,
      @Valid @RequestBody EditMessageRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(messages.edit(requireCaller(authentication), messageId, request));
  }

  @DeleteMapping("/messages/{messageId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID messageId, Authentication authentication) {
    messages.delete(requireCaller(authentication), messageId);
    return ResponseEntity.noContent().build();
  }

  private UUID requireCaller(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
    return userId;
  }
}
