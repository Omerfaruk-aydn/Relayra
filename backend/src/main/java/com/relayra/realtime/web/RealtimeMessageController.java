package com.relayra.realtime.web;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.message.MessageService;
import com.relayra.message.dto.MessageResponse;
import com.relayra.message.dto.SendMessageRequest;
import com.relayra.realtime.dto.RealtimeError;
import com.relayra.realtime.dto.RealtimeEvent;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
public class RealtimeMessageController {

  private final MessageService messages;

  public RealtimeMessageController(MessageService messages) {
    this.messages = messages;
  }

  @MessageMapping("/channels/{channelId}/messages")
  @SendToUser("/queue/acks")
  public RealtimeEvent<MessageResponse> send(
      @DestinationVariable UUID channelId,
      @Valid @Payload SendMessageRequest request,
      Principal principal) {
    UUID callerId = requireCaller(principal);
    MessageResponse response = messages.sendToChannel(callerId, channelId, request);
    return new RealtimeEvent<>(UUID.randomUUID(), "MESSAGE_ACK", Instant.now(), response);
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

  private UUID requireCaller(Principal principal) {
    if (principal == null) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
    try {
      return UUID.fromString(principal.getName());
    } catch (IllegalArgumentException exception) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
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
}
