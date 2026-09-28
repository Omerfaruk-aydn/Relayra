package com.relayra.realtime;

import java.security.Principal;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class PresenceSessionListener {

  private final PresenceService presence;

  public PresenceSessionListener(PresenceService presence) {
    this.presence = presence;
  }

  @EventListener
  public void onConnect(SessionConnectedEvent event) {
    UUID userId = principalUserId(event.getUser());
    if (userId != null) {
      String sessionId =
          SimpMessageHeaderAccessor.wrap(event.getMessage()).getSessionId();
      presence.connected(userId, sessionId);
      presence.heartbeat(userId);
    }
  }

  @EventListener
  public void onDisconnect(SessionDisconnectEvent event) {
    UUID userId = principalUserId(event.getUser());
    if (userId != null) {
      presence.disconnected(userId, event.getSessionId());
    }
  }

  private UUID principalUserId(Principal principal) {
    if (principal instanceof UsernamePasswordAuthenticationToken authentication
        && authentication.getPrincipal() instanceof UUID userId) {
      return userId;
    }
    if (principal != null) {
      try {
        return UUID.fromString(principal.getName());
      } catch (IllegalArgumentException exception) {
        return null;
      }
    }
    return null;
  }
}
