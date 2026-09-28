package com.relayra.realtime;

import com.relayra.auth.JwtService;
import com.relayra.auth.domain.UserStatus;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.channel.persistence.ChannelRepository;
import com.relayra.role.PermissionService;
import com.relayra.role.domain.Permission;
import io.jsonwebtoken.JwtException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class StompOutboundAuthorizationInterceptor implements ChannelInterceptor {

  private static final Pattern CHANNEL_MESSAGES_TOPIC =
      Pattern.compile("^/topic/channels/([0-9a-fA-F-]{36})/messages$");

  private final JwtService jwtService;
  private final UserRepository users;
  private final ChannelRepository channels;
  private final PermissionService permissions;

  public StompOutboundAuthorizationInterceptor(
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
    if (accessor == null
        || accessor.getMessageType() != SimpMessageType.MESSAGE
        || accessor.getDestination() == null) {
      return message;
    }
    Matcher matcher = CHANNEL_MESSAGES_TOPIC.matcher(accessor.getDestination());
    if (!matcher.matches()) {
      return message;
    }
    if (!(accessor.getUser() != null
        && accessor.getUser().getName() != null
        && accessor.getSessionAttributes() != null)) {
      return null;
    }
    try {
      UUID userId = UUID.fromString(accessor.getUser().getName());
      Map<String, Object> attributes = accessor.getSessionAttributes();
      Object tokenValue = attributes.get(StompAuthenticationInterceptor.TOKEN_SESSION_KEY);
      if (!(tokenValue instanceof String token)
          || !jwtService.parseUserId(token).equals(userId)
          || users
              .findById(userId)
              .filter(user -> user.getStatus() == UserStatus.ACTIVE)
              .isEmpty()) {
        return null;
      }
      UUID channelId = UUID.fromString(matcher.group(1));
      return channels
              .findById(channelId)
              .filter(
                  target ->
                      permissions.has(userId, target.getCommunityId(), Permission.VIEW_CHANNEL))
              .isPresent()
          ? message
          : null;
    } catch (JwtException | IllegalArgumentException exception) {
      return null;
    }
  }
}
