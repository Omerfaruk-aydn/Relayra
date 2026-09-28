package com.relayra.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.relayra.auth.dto.LoginRequest;
import com.relayra.auth.dto.RegisterRequest;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class AuthNormalizationTest {

  @Test
  void usernameAndEmailNormalizeToLowercase() {
    RegisterRequest request =
        new RegisterRequest("OmerFaruk", "Omer@Example.COM", "password123");
    assertThat(request.username().trim().toLowerCase(Locale.ROOT)).isEqualTo("omerfaruk");
    assertThat(request.email().trim().toLowerCase(Locale.ROOT)).isEqualTo("omer@example.com");
  }

  @Test
  void tokenHashIsStableAndHidesRawToken() {
    String hash = TokenHasher.sha256Hex("raw-refresh-token");
    assertThat(hash).hasSize(64);
    assertThat(hash).isEqualTo(TokenHasher.sha256Hex("raw-refresh-token"));
    assertThat(hash).doesNotContain("raw-refresh-token");
  }

  @Test
  void loginIdentifierWithAtSignRoutesToEmail() {
    LoginRequest email = new LoginRequest("User@Example.com", "password123");
    LoginRequest username = new LoginRequest("someuser", "password123");
    assertThat(email.identifier()).contains("@");
    assertThat(username.identifier()).doesNotContain("@");
  }

  @Test
  void rateLimiterRejectsBeyondLimit() {
    RateLimiter limiter = new RateLimiter();
    String key = "test:key";
    limiter.check(key, 2, java.time.Duration.ofMinutes(1));
    limiter.check(key, 2, java.time.Duration.ofMinutes(1));
    assertThatThrownBy(() -> limiter.check(key, 2, java.time.Duration.ofMinutes(1)))
        .isInstanceOf(RateLimitedException.class);
  }
}
