package com.relayra.auth;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

  private record Bucket(AtomicLong count, long windowStartMillis) {}

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  public void check(String key, int maxPerWindow, Duration window) {
    long now = System.currentTimeMillis();
    long windowMillis = window.toMillis();
    Bucket bucket =
        buckets.compute(
            key,
            (k, existing) -> {
              if (existing == null || now - existing.windowStartMillis() >= windowMillis) {
                return new Bucket(new AtomicLong(1), now);
              }
              existing.count().incrementAndGet();
              return existing;
            });
    if (bucket.count().get() > maxPerWindow) {
      long retryAfter =
          Math.max(1, (windowMillis - (now - bucket.windowStartMillis())) / 1000);
      throw new RateLimitedException(retryAfter);
    }
  }
}
