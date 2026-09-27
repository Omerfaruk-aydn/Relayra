package com.relayra.auth;

import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import java.time.Duration;
import java.util.UUID;

public class RateLimitedException extends DomainException {

  private final long retryAfterSeconds;

  public RateLimitedException(long retryAfterSeconds) {
    super(429, ErrorCodes.RATE_LIMITED, "Too many requests. Try again later.");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }

  public static String deviceKey(String prefix, UUID userId) {
    return prefix + ":user:" + userId;
  }

  public static String ipKey(String prefix, String ip) {
    return prefix + ":ip:" + (ip == null ? "unknown" : ip);
  }

  public static Duration windowOf(long seconds) {
    return Duration.ofSeconds(seconds);
  }
}
