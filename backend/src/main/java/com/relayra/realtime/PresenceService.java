package com.relayra.realtime;

import com.relayra.realtime.dto.TypingSignal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PresenceService {

  static final String KEY_PREFIX = "relayra:presence:";
  static final String FRIENDS_PREFIX = "relayra:presence:friends:";
  static final long ONLINE_TTL_SECONDS = 30;
  private static final long TYPING_TTL_SECONDS = 5;
  private static final String FRIEND_SEPARATOR = ",";

  private final PresenceStore store;
  private final SimpMessagingTemplate messagingTemplate;
  private final Map<UUID, Set<String>> sessions = new ConcurrentHashMap<>();
  private final Map<UUID, Instant> disconnectAt = new ConcurrentHashMap<>();
  private final Map<String, Instant> typingExpires = new ConcurrentHashMap<>();
  private final Duration gracePeriod;

  public PresenceService(
      PresenceStore store,
      SimpMessagingTemplate messagingTemplate,
      @Value("${relayra.presence.grace-seconds:30}") long graceSeconds) {
    this.store = store;
    this.messagingTemplate = messagingTemplate;
    this.gracePeriod = Duration.ofSeconds(Math.max(0, graceSeconds));
  }

  public void connected(UUID userId, String sessionId) {
    disconnectAt.remove(userId);
    boolean wasOffline = sessions.getOrDefault(userId, Set.of()).isEmpty();
    sessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
    heartbeat(userId);
    if (wasOffline) {
      broadcast(userId, "ONLINE");
    }
  }

  public void disconnected(UUID userId, String sessionId) {
    Set<String> owned = sessions.get(userId);
    if (owned == null || sessionId == null) {
      return;
    }
    owned.remove(sessionId);
    if (owned.isEmpty()) {
      disconnectAt.put(userId, Instant.now());
    }
  }

  public void heartbeat(UUID userId) {
    store.set(KEY_PREFIX + userId, Instant.now().toString(), Duration.ofSeconds(ONLINE_TTL_SECONDS));
    Set<UUID> friends = readFriends(userId);
    if (!friends.isEmpty()) {
      String friendsText =
          friends.stream().map(UUID::toString).collect(Collectors.joining(FRIEND_SEPARATOR));
      store.set(FRIENDS_PREFIX + userId, friendsText, Duration.ofMinutes(5));
    }
  }

  public boolean isOnline(UUID userId) {
    Instant disconnected = disconnectAt.get(userId);
    if (disconnected != null
        && Instant.now().isAfter(disconnected.plus(gracePeriod))) {
      disconnectAt.remove(userId);
      sessions.remove(userId);
      return false;
    }
    if (!sessions.getOrDefault(userId, Set.of()).isEmpty()) {
      return true;
    }
    return store.hasKey(KEY_PREFIX + userId);
  }

  public Map<UUID, String> statusesFor(Set<UUID> userIds) {
    return userIds.stream()
        .collect(Collectors.toMap(userId -> userId, this::statusFor, (first, second) -> first));
  }

  public String statusFor(UUID userId) {
    return isOnline(userId) ? "ONLINE" : "OFFLINE";
  }

  public boolean isTyping(UUID userId, UUID channelId) {
    Instant expires = typingExpires.get(typingKey(userId, channelId));
    if (expires == null) {
      return false;
    }
    if (Instant.now().isAfter(expires)) {
      typingExpires.remove(typingKey(userId, channelId));
      return false;
    }
    return true;
  }

  TypingSignal markTyping(UUID userId, UUID channelId, Instant expiresAt) {
    typingExpires.put(typingKey(userId, channelId), expiresAt);
    return new TypingSignal(userId, channelId, true);
  }

  void clearTyping(UUID userId, UUID channelId) {
    typingExpires.remove(typingKey(userId, channelId));
  }

  public void publishStoppedTyping(TypingSignal signal) {
    clearTyping(signal.userId(), signal.channelId());
    publishTyping(signal);
  }

  public void publishTyping(TypingSignal signal) {
    messagingTemplate.convertAndSend(
        "/topic/channels/" + signal.channelId() + "/typing",
        new com.relayra.realtime.dto.RealtimeEvent<>(
            UUID.randomUUID(), "TYPING", Instant.now(), signal));
  }

  public void watchFriends(UUID userId, Set<UUID> friendIds) {
    if (!friendIds.isEmpty()) {
      store.set(
          FRIENDS_PREFIX + userId,
          friendIds.stream().map(UUID::toString).collect(Collectors.joining(FRIEND_SEPARATOR)),
          Duration.ofMinutes(5));
    }
  }

  @Scheduled(fixedDelay = 10_000)
  void sweep() {
    Instant now = Instant.now();
    disconnectAt.forEach(
        (userId, disconnected) -> {
          if (!now.isBefore(disconnected.plus(gracePeriod))
              && sessions.getOrDefault(userId, Set.of()).isEmpty()) {
            disconnectAt.remove(userId);
            sessions.remove(userId);
            broadcast(userId, "OFFLINE");
          }
        });
    typingExpires.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
  }

  private void broadcast(UUID userId, String status) {
    messagingTemplate.convertAndSend(
        "/topic/presence/" + userId,
        new com.relayra.realtime.dto.RealtimeEvent<>(
            UUID.randomUUID(),
            "PRESENCE_CHANGED",
            Instant.now(),
            new com.relayra.realtime.dto.PresenceEventData(userId, status, Instant.now())));
  }

  private Set<UUID> readFriends(UUID userId) {
    return store.readUuidSet(FRIENDS_PREFIX + userId);
  }

  private String typingKey(UUID userId, UUID channelId) {
    return userId + ":" + channelId;
  }
}
