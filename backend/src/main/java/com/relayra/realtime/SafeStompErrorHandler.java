package com.relayra.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.realtime.dto.RealtimeError;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Component
public class SafeStompErrorHandler extends StompSubProtocolErrorHandler {

  private final ObjectMapper objectMapper;

  public SafeStompErrorHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Message<byte[]> handleClientMessageProcessingError(
      Message<byte[]> clientMessage, Throwable exception) {
    DomainException domain = findDomainException(exception);
    String code = domain == null ? ErrorCodes.INTERNAL_ERROR : domain.getCode();
    String message = domain == null ? "WebSocket request was rejected." : domain.getMessage();
    byte[] payload;
    try {
      payload =
          objectMapper.writeValueAsBytes(
              RealtimeError.of(UUID.randomUUID().toString(), code, message));
    } catch (JsonProcessingException serializationException) {
      payload = "{\"type\":\"ERROR\",\"code\":\"INTERNAL_ERROR\"}".getBytes();
    }
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
    accessor.setMessage("WebSocket request was rejected.");
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
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
