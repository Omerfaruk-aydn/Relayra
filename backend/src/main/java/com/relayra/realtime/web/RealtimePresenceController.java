package com.relayra.realtime.web;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.realtime.dto.RealtimeError;
import com.relayra.realtime.TypingService;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
public class RealtimePresenceController {

  private final TypingService typing;

  public RealtimePresenceController(TypingService typing) {
    this.typing = typing;
  }

  @MessageMapping("/channels/{channelId}/typing")
  public void typing(
      @DestinationVariable UUID channelId,
      @Payload(required = false) Map<String, Boolean> payload,
      Principal principal) {
    boolean isTyping = payload != null && Boolean.TRUE.equals(payload.get("typing"));
    typing.signal(requireCaller(principal), channelId, isTyping);
  }

  @MessageExceptionHandler
  @SendToUser("/queue/errors")
  public RealtimeError handle(Throwable exception) {
    DomainException domain = findDomainException(exception);
    if (domain == null) {
      return RealtimeError.of(
          UUID.randomUUID().toString(),
          ErrorCodes.INTERNAL_ERROR,
          "WebSocket request was rejected.");
    }
    return RealtimeError.of(
        UUID.randomUUID().toString(), domain.getCode(), domain.getMessage());
  }

  private DomainException findDomainException(Throwable exception) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof DomainException domain) {
        return domain;
      }
      current = current.getCause();
    }
    return null;
  }

  private UUID requireCaller(Principal principal) {
    if (principal == null) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
    try {
      return UUID.fromString(principal.getName());
    } catch (IllegalArgumentException cause) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
  }
}
