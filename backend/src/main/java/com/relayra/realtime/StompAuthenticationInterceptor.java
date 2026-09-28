package com.relayra.realtime;

import com.relayra.auth.JwtService;
import com.relayra.auth.domain.User;
import com.relayra.auth.domain.UserStatus;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.channel.domain.Channel;
import com.relayra.channel.persistence.ChannelRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.role.PermissionService;
import com.relayra.role.domain.Permission;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {

  private static final Pattern CHANNEL_MESSAGES_TOPIC =
      Pattern.compile("^/topic/channels/([0-9a-fA-F-]{36})/messages$");
  private static final Pattern CHANNEL_MESSAGES_SEND =
      Pattern.compile("^/app/channels/([0-9a-fA-F-]{36})/messages$");
  static final String TOKEN_SESSION_KEY = "relayra.accessToken";
  private static final String SUBSCRIPTIONS_SESSION_KEY = "relayra.subscriptions";
  private static final int MAX_SUBSCRIPTIONS_PER_SESSION = 100;
  private static final int MAX_SESSIONS_PER_USER = 5;

  private final Map<UUID, Set<String>> userSessions = new ConcurrentHashMap<>();
  private final JwtService jwtService;
  private final UserRepository users;
  private final ChannelRepository channels;
  private final PermissionService permissions;

  public StompAuthenticationInterceptor(
      JwtService jwtService,
      UserRepository users,
      ChannelRepository channels,
      PermissionService permissions) {
    this.jwtService = jwtService;
    this.users = users;
    this.channels = channels;
    this.permissions = permissions;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null) {
      return message;
    }
    StompCommand command = accessor.getCommand();
    if (command == null) {
      return message;
    }
    if (command == StompCommand.CONNECT) {
      authenticate(accessor);
      return message;
    }
    if (command == StompCommand.DISCONNECT) {
      untrackSession(accessor);
      return message;
    }
    UUID userId = requireUser(accessor);
    if (command == StompCommand.SUBSCRIBE) {
      authorizeSubscription(userId, accessor.getDestination());
      trackSubscription(accessor);
    } else if (command == StompCommand.UNSUBSCRIBE) {
      untrackSubscription(accessor);
    } else if (command == StompCommand.SEND) {
      authorizeSendDestination(userId, accessor.getDestination());
    }
    return message;
  }

  private void authenticate(StompHeaderAccessor accessor) {
    List<String> values = accessor.getNativeHeader(HttpHeaders.AUTHORIZATION);
    if (values == null || values.size() != 1 || !values.get(0).startsWith("Bearer ")) {
      throw authenticationRequired();
    }
    String token = values.get(0).substring(7).trim();
    try {
      UUID userId = jwtService.parseUserId(token);
      User user =
          users
              .findById(userId)
              .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
              .orElseThrow(this::authenticationRequired);
      Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
      if (sessionAttributes == null) {
        throw authenticationRequired();
      }
      trackSession(user.getId(), accessor.getSessionId());
      sessionAttributes.put(TOKEN_SESSION_KEY, token);
      sessionAttributes.put(SUBSCRIPTIONS_SESSION_KEY, ConcurrentHashMap.<String>newKeySet());
      accessor.setUser(
          new UsernamePasswordAuthenticationToken(
              user.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    } catch (JwtException | IllegalArgumentException exception) {
      throw new DomainException(
          HttpStatus.UNAUTHORIZED.value(), ErrorCodes.TOKEN_INVALID, "Token is invalid.");
    }
  }

  private UUID requireUser(StompHeaderAccessor accessor) {
    if (accessor.getUser() == null
        || !(accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication)
        || !(authentication.getPrincipal() instanceof UUID userId)
        || accessor.getSessionAttributes() == null
        || !(accessor.getSessionAttributes().get(TOKEN_SESSION_KEY) instanceof String token)) {
      throw authenticationRequired();
    }
    try {
      UUID tokenUserId = jwtService.parseUserId(token);
      if (!tokenUserId.equals(userId)
          || users
              .findById(userId)
              .filter(user -> user.getStatus() == UserStatus.ACTIVE)
              .isEmpty()) {
        throw authenticationRequired();
      }
    } catch (JwtException | IllegalArgumentException exception) {
      throw new DomainException(
          HttpStatus.UNAUTHORIZED.value(), ErrorCodes.TOKEN_INVALID, "Token is invalid.");
    }
    return userId;
  }

  private void authorizeSubscription(UUID userId, String destination) {
    if ("/user/queue/acks".equals(destination) || "/user/queue/errors".equals(destination)) {
      return;
    }
    Matcher matcher = destination == null ? null : CHANNEL_MESSAGES_TOPIC.matcher(destination);
    if (matcher == null || !matcher.matches()) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Subscription destination is not supported.");
    }
    UUID channelId = parseUuid(matcher.group(1));
    Channel channel =
        channels
            .findById(channelId)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "Channel was not found."));
    permissions.require(userId, channel.getCommunityId(), Permission.VIEW_CHANNEL);
  }

  private void authorizeSendDestination(UUID userId, String destination) {
    Matcher matcher = destination == null ? null : CHANNEL_MESSAGES_SEND.matcher(destination);
    if (matcher == null || !matcher.matches()) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Send destination is not supported.");
    }
    UUID channelId = parseUuid(matcher.group(1));
    Channel channel =
        channels
            .findById(channelId)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "Channel was not found."));
    permissions.require(userId, channel.getCommunityId(), Permission.SEND_MESSAGES);
  }

  private void trackSession(UUID userId, String sessionId) {
    if (sessionId == null) {
      throw authenticationRequired();
    }
    Set<String> sessions =
        userSessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());
    synchronized (sessions) {
      if (!sessions.contains(sessionId) && sessions.size() >= MAX_SESSIONS_PER_USER) {
        throw new DomainException(429, ErrorCodes.RATE_LIMITED, "Too many active sessions.");
      }
      sessions.add(sessionId);
    }
  }

  private void untrackSession(StompHeaderAccessor accessor) {
    if (accessor.getUser() == null
        || !(accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication)
        || !(authentication.getPrincipal() instanceof UUID userId)
        || accessor.getSessionId() == null) {
      return;
    }
    Set<String> sessions = userSessions.get(userId);
    if (sessions != null) {
      sessions.remove(accessor.getSessionId());
      if (sessions.isEmpty()) {
        userSessions.remove(userId, sessions);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void trackSubscription(StompHeaderAccessor accessor) {
    Map<String, Object> attributes = accessor.getSessionAttributes();
    if (attributes == null || accessor.getSubscriptionId() == null) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Subscription id is required.");
    }
    Set<String> subscriptions =
        (Set<String>)
            attributes.computeIfAbsent(
                SUBSCRIPTIONS_SESSION_KEY, ignored -> ConcurrentHashMap.<String>newKeySet());
    if (!subscriptions.contains(accessor.getSubscriptionId())
        && subscriptions.size() >= MAX_SUBSCRIPTIONS_PER_SESSION) {
      throw new DomainException(
          429, ErrorCodes.RATE_LIMITED, "Too many active subscriptions.");
    }
    subscriptions.add(accessor.getSubscriptionId());
  }

  @SuppressWarnings("unchecked")
  private void untrackSubscription(StompHeaderAccessor accessor) {
    Map<String, Object> attributes = accessor.getSessionAttributes();
    if (attributes == null || accessor.getSubscriptionId() == null) {
      return;
    }
    Object value = attributes.get(SUBSCRIPTIONS_SESSION_KEY);
    if (value instanceof Set<?> subscriptions) {
      ((Set<String>) subscriptions).remove(accessor.getSubscriptionId());
    }
  }

  private UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(), ErrorCodes.VALIDATION_FAILED, "Destination is invalid.");
    }
  }

  private DomainException authenticationRequired() {
    return new DomainException(
        HttpStatus.UNAUTHORIZED.value(),
        ErrorCodes.AUTHENTICATION_REQUIRED,
        "Authentication is required.");
  }
}
