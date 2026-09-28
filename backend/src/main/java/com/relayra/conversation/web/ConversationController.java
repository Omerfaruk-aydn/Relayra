package com.relayra.conversation.web;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.conversation.ConversationService;
import com.relayra.conversation.dto.ConversationResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

  private final ConversationService conversations;

  public ConversationController(ConversationService conversations) {
    this.conversations = conversations;
  }

  @PostMapping("/direct/{userId}")
  public ResponseEntity<ConversationResponse> direct(
      @PathVariable UUID userId, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(conversations.direct(requireCaller(authentication), userId));
  }

  @GetMapping
  public ResponseEntity<List<ConversationResponse>> list(Authentication authentication) {
    return ResponseEntity.ok(conversations.list(requireCaller(authentication)));
  }

  @GetMapping("/{conversationId}")
  public ResponseEntity<ConversationResponse> get(
      @PathVariable UUID conversationId, Authentication authentication) {
    return ResponseEntity.ok(conversations.get(requireCaller(authentication), conversationId));
  }

  private UUID requireCaller(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
    return userId;
  }
}
