package com.relayra.realtime;

import com.relayra.channel.domain.Channel;
import com.relayra.channel.persistence.ChannelRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.realtime.dto.TypingSignal;
import com.relayra.role.PermissionService;
import com.relayra.role.domain.Permission;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TypingService {

  private final ChannelRepository channels;
  private final PermissionService permissions;
  private final PresenceService presence;
  private final com.relayra.auth.RateLimiter rateLimiter;

  public TypingService(
      ChannelRepository channels,
      PermissionService permissions,
      PresenceService presence,
      com.relayra.auth.RateLimiter rateLimiter) {
    this.channels = channels;
    this.permissions = permissions;
    this.presence = presence;
    this.rateLimiter = rateLimiter;
  }

  public void signal(UUID callerId, UUID channelId, boolean typing) {
    Channel channel =
        channels
            .findById(channelId)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "Channel was not found."));
    permissions.require(callerId, channel.getCommunityId(), Permission.SEND_MESSAGES);
    rateLimiter.check(
        com.relayra.auth.RateLimitedException.deviceKey("typing", callerId),
        60,
        Duration.ofMinutes(1));
    if (typing) {
      presence.publishTyping(
          presence.markTyping(callerId, channelId, Instant.now().plusSeconds(5)));
    } else {
      presence.publishStoppedTyping(new TypingSignal(callerId, channelId, false));
    }
  }
}
