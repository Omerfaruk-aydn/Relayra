package com.relayra.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RateLimiter rateLimiter;

  @Test
  void inMemoryLimiterRejectsBeyondLimit() throws Exception {
    String key = "test-key-" + System.nanoTime();
    for (int i = 0; i < 3; i++) {
      rateLimiter.check(key, 3, java.time.Duration.ofMinutes(1));
    }
    try {
      rateLimiter.check(key, 3, java.time.Duration.ofMinutes(1));
      throw new AssertionError("Expected RateLimitedException");
    } catch (RateLimitedException e) {
      assert e.getRetryAfterSeconds() >= 1;
    }
  }

  @Test
  void invalidRegisterPayloadIsValidationError() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"x\",\"email\":\"bad\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(header().exists("X-Request-Id"));
  }
}
