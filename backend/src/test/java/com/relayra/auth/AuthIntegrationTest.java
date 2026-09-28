package com.relayra.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayra.auth.web.AuthController;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void registerLoginRefreshLogoutFlow() throws Exception {
    String username = "authuser" + System.nanoTime() % 100000;
    String email = username + "@example.com";

    MvcResult registerResult =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\""
                            + username
                            + "\",\"email\":\""
                            + email
                            + "\",\"password\":\"password123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.username").value(username))
            .andExpect(cookie().exists(AuthController.REFRESH_COOKIE))
            .andReturn();
    String refreshCookie = extractRefreshCookie(registerResult);
    String accessToken = accessToken(registerResult);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"identifier\":\""
                        + email
                        + "\",\"password\":\"password123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());

    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(username));

    MvcResult refreshResult =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .cookie(new Cookie(AuthController.REFRESH_COOKIE, refreshCookie)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(cookie().exists(AuthController.REFRESH_COOKIE))
            .andReturn();
    String rotatedCookie = extractRefreshCookie(refreshResult);

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .cookie(new Cookie(AuthController.REFRESH_COOKIE, refreshCookie)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .cookie(new Cookie(AuthController.REFRESH_COOKIE, rotatedCookie)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .cookie(new Cookie(AuthController.REFRESH_COOKIE, rotatedCookie)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void registerDuplicateUsernameConflicts() throws Exception {
    String username = "dupeuser" + System.nanoTime() % 100000;
    String body =
        "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"password123\"}";
    mockMvc
        .perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\""
                        + username
                        + "\",\"email\":\"other-"
                        + username
                        + "@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
  }

  @Test
  void registerValidationFails() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ab\",\"email\":\"bad\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.username").isNotEmpty());
  }

  @Test
  void loginWrongPasswordIsUnauthorized() throws Exception {
    String username = "loginfail" + System.nanoTime() % 100000;
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\""
                        + username
                        + "\",\"email\":\""
                        + username
                        + "@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"identifier\":\"" + username + "\",\"password\":\"wrongpassword\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void meWithoutTokenIsForbidden() throws Exception {
    mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isForbidden());
  }

  @Test
  void meWithTamperedTokenIsForbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer tampered.token.here"))
        .andExpect(status().isForbidden());
  }

  private String extractRefreshCookie(MvcResult result) {
    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    String prefix = AuthController.REFRESH_COOKIE + "=";
    int start = setCookie.indexOf(prefix) + prefix.length();
    int end = setCookie.indexOf(';', start);
    return setCookie.substring(start, end);
  }

  private String accessToken(MvcResult result) throws Exception {
    JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
    return node.get("accessToken").asText();
  }
}
