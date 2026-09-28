package com.relayra.reaction.web;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.reaction.ReactionService;
import com.relayra.reaction.dto.ReactionResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/messages/{messageId}/reactions")
public class ReactionController {

  private final ReactionService reactions;

  public ReactionController(ReactionService reactions) {
    this.reactions = reactions;
  }

  @PutMapping("/{emoji}")
  public ResponseEntity<List<ReactionResponse>> add(
      @PathVariable UUID messageId, @PathVariable String emoji, Authentication authentication) {
    return ResponseEntity.ok(reactions.add(requireCaller(authentication), messageId, emoji));
  }

  @DeleteMapping("/{emoji}")
  public ResponseEntity<Void> remove(
      @PathVariable UUID messageId, @PathVariable String emoji, Authentication authentication) {
    reactions.remove(requireCaller(authentication), messageId, emoji);
    return ResponseEntity.noContent().build();
  }

  @GetMapping
  public ResponseEntity<List<ReactionResponse>> list(
      @PathVariable UUID messageId, Authentication authentication) {
    return ResponseEntity.ok(reactions.list(requireCaller(authentication), messageId));
  }

  private UUID requireCaller(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
    return userId;
  }
}
