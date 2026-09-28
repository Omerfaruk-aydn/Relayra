package com.relayra.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class UserIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void profileReadUpdateAndSearchFlow() throws Exception {
    String username = "profileuser" + System.nanoTime() % 100000;
    Session first = register(username);
    Session second = register("searchbuddy" + System.nanoTime() % 100000);

    mockMvc
        .perform(
            get("/api/v1/users/" + first.userId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(username))
        .andExpect(jsonPath("$.timezone").doesNotExist())
        .andExpect(jsonPath("$.avatarKey").doesNotExist());

    mockMvc
        .perform(
            patch("/api/v1/users/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"displayName\":\"New Name\",\"bio\":\"Hello world\",\"timezone\":\"Europe/Istanbul\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("New Name"))
        .andExpect(jsonPath("$.bio").value("Hello world"));

    mockMvc
        .perform(
            patch("/api/v1/users/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\":\"Only bio changed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("New Name"))
        .andExpect(jsonPath("$.bio").value("Only bio changed"))
        .andExpect(jsonPath("$.timezone").value("Europe/Istanbul"));

    mockMvc
        .perform(
            get("/api/v1/users/" + first.userId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + second.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bio").value("Only bio changed"))
        .andExpect(jsonPath("$.timezone").doesNotExist());

    mockMvc
        .perform(
            get("/api/v1/users/00000000-0000-0000-0000-000000000000")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            get("/api/v1/users/search")
                .queryParam("q", second.username().substring(0, 8))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].username").value(second.username()));
  }

  @Test
  void searchRejectsShortQuery() throws Exception {
    Session session = register("shortq" + System.nanoTime() % 100000);
    mockMvc
        .perform(
            get("/api/v1/users/search")
                .queryParam("q", "a")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void searchRejectsHugeLimit() throws Exception {
    Session session = register("biglimit" + System.nanoTime() % 100000);
    mockMvc
        .perform(
            get("/api/v1/users/search")
                .queryParam("q", "biglimit")
                .queryParam("limit", "500")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void profileUpdateRejectsOversizedBio() throws Exception {
    Session session = register("bigbio" + System.nanoTime() % 100000);
    String bio = "x".repeat(501);
    mockMvc
        .perform(
            patch("/api/v1/users/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\":\"" + bio + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void profileUpdateRejectsOversizedLimit() throws Exception {
    Session session = register("biglimit" + System.nanoTime() % 100000);
    mockMvc
        .perform(
            patch("/api/v1/users/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"" + "y".repeat(65) + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void searchRejectsOutOfRangeLimits() throws Exception {
    Session session = register("rangeq" + System.nanoTime() % 100000);
    for (String limit : new String[] {"0", "-1", "51"}) {
      mockMvc
          .perform(
              get("/api/v1/users/search")
                  .queryParam("q", "rangeq")
                  .queryParam("limit", limit)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void unauthenticatedProfileReadIsForbidden() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/00000000-0000-0000-0000-000000000000"))
        .andExpect(status().isForbidden());
  }

  @Test
  void tamperedTokenCannotSearch() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/users/search")
                .queryParam("q", "somebody")
                .header(HttpHeaders.AUTHORIZATION, "Bearer tampered.token.here"))
        .andExpect(status().isForbidden());
  }

  private Session register(String username) throws Exception {
    MvcResult result =
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
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
    return new Session(
        node.get("user").get("id").asText(),
        node.get("user").get("username").asText(),
        node.get("accessToken").asText());
  }

  private record Session(String userId, String username, String token) {}
}
