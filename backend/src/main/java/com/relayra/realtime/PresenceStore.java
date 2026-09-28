package com.relayra.realtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class PresenceStore {

  private final StringRedisTemplate redis;
  private final boolean failoverEnabled;
  private final Map<String, Instant> expirations = new ConcurrentHashMap<>();
  private final Map<String, String> values = new ConcurrentHashMap<>();

  public PresenceStore(
      StringRedisTemplate redis,
      @Value("${relayra.presence.redis-failover:true}") boolean failoverEnabled) {
    this.redis = redis;
    this.failoverEnabled = failoverEnabled;
  }

  public void set(String key, String value, Duration ttl) {
    try {
      redis.opsForValue().set(key, value, ttl);
      values.remove(key);
      expirations.remove(key);
    } catch (RuntimeException exception) {
      if (!failoverEnabled) {
        throw exception;
      }
      values.put(key, value);
      expirations.put(key, Instant.now().plus(ttl));
    }
  }

  public Optional<String> get(String key) {
    try {
      return Optional.ofNullable(redis.opsForValue().get(key));
    } catch (RuntimeException exception) {
      if (!failoverEnabled) {
        throw exception;
      }
      String value = values.get(key);
      Instant expires = expirations.get(key);
      if (value == null || expires == null || !Instant.now().isBefore(expires)) {
        values.remove(key);
        expirations.remove(key);
        return Optional.empty();
      }
      return Optional.of(value);
    }
  }

  public boolean hasKey(String key) {
    try {
      return Boolean.TRUE.equals(redis.hasKey(key));
    } catch (RuntimeException exception) {
      if (!failoverEnabled) {
        throw exception;
      }
      return get(key).isPresent();
    }
  }

  public Set<UUID> readUuidSet(String key) {
    return get(key)
        .map(
            value ->
                java.util.Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(candidate -> !candidate.isEmpty())
                    .map(
                        candidate -> {
                          try {
                            return UUID.fromString(candidate);
                          } catch (IllegalArgumentException exception) {
                            return null;
                          }
                        })
                    .filter(candidate -> candidate != null)
                    .collect(java.util.stream.Collectors.toSet()))
        .orElse(Set.of());
  }
}
