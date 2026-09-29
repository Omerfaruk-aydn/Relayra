package com.relayra.friendship;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FriendshipBlockMessageTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void blockedPeerCannotDm() throws Exception {
    Session alice = register("blockdmalice" + nanos());
    Session bob = register("blockdmbob" + nanos());
    String tag = nanos();

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/conversations/direct/" + bob.userId()).headers(authHeader(alice)))
            .andExpect(status().isCreated())
            .andReturn();
    String conversationId = id(created);

    mockMvc
        .perform(post("/api/v1/users/" + bob.userId() + "/block").headers(authHeader(alice)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/conversations/" + conversationId + "/messages")
                .headers(authHeader(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientMessageId\":\"blocked-dm-" + tag + "\",\"content\":\"hello\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void acceptAfterBlockFails() throws Exception {
    Session alice = register("blockacceptalice" + nanos());
    Session bob = register("blockacceptbob" + nanos());

    String requestId =
        id(
            mockMvc
                .perform(
                    post("/api/v1/friends/requests")
                        .headers(authHeader(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":\"" + bob.userId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    mockMvc
        .perform(post("/api/v1/users/" + bob.userId() + "/block").headers(authHeader(alice)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/friends/requests/" + requestId + "/accept").headers(authHeader(bob)))
        .andExpect(status().isNotFound());
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
        node.get("user").get("id").asText(), node.get("accessToken").asText());
  }

  private org.springframework.http.HttpHeaders authHeader(Session s) {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + s.token());
    return headers;
  }

  private String id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private String nanos() {
    return UUID.randomUUID().toString().substring(0, 8).replace("-", "a");
  }

  private record Session(String userId, String token) {}
}
