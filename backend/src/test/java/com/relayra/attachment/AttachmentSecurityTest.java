package com.relayra.attachment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttachmentSecurityTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void outsiderCannotFinalizeOthersUpload() throws Exception {
    Session owner = register("atsecowner" + nanos());
    Session outsider = register("atsecoutsider" + nanos());
    String communityId = createCommunity(owner, "Attachment Owner Gate");
    String channelId = firstChannelId(owner, communityId);
    String messageId = sendChannel(owner, channelId, "owner-msg-" + nanos(), "owner message");

    MvcResult uploaded =
        mockMvc
            .perform(
                multipart("/api/v1/uploads")
                    .file(
                        new MockMultipartFile(
                            "file", "owner.txt", "text/plain", "owner file".getBytes()))
                    .param("channelId", channelId)
                    .headers(auth(owner)))
            .andExpect(status().isCreated())
            .andReturn();
    String attachmentId =
        objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/messages/" + messageId + "/attachments")
                .headers(auth(outsider))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentIds\":[\"" + attachmentId + "\"]}"))
        .andExpect(status().is4xxClientError())
        .andExpect(
            result -> {
              int responseStatus = result.getResponse().getStatus();
              if (responseStatus != 403 && responseStatus != 404) {
                throw new AssertionError("Expected status 403 or 404 but was " + responseStatus);
              }
            });
  }

  @Test
  void crossScopeFinalizeRejected() throws Exception {
    Session owner = register("atcrossowner" + nanos());
    String communityId = createCommunity(owner, "Attachment Scope Gate");
    String channelA = firstChannelId(owner, communityId);

    MvcResult createdChannel =
        mockMvc
            .perform(
                post("/api/v1/communities/" + communityId + "/channels")
                    .headers(auth(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"xx\",\"type\":\"TEXT\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String channelB =
        objectMapper.readTree(createdChannel.getResponse().getContentAsString()).get("id").asText();
    String messageId = sendChannel(owner, channelB, "cross-msg-" + nanos(), "other channel");

    MvcResult uploaded =
        mockMvc
            .perform(
                multipart("/api/v1/uploads")
                    .file(
                        new MockMultipartFile(
                            "file", "scoped.txt", "text/plain", "scoped file".getBytes()))
                    .param("channelId", channelA)
                    .headers(auth(owner)))
            .andExpect(status().isCreated())
            .andReturn();
    String attachmentId =
        objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/messages/" + messageId + "/attachments")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentIds\":[\"" + attachmentId + "\"]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void relinkToAnotherMessageConflict() throws Exception {
    Session owner = register("atrelinkowner" + nanos());
    String communityId = createCommunity(owner, "Attachment Relink Gate");
    String channelId = firstChannelId(owner, communityId);
    String messageOne = sendChannel(owner, channelId, "relink-1-" + nanos(), "first message");
    String messageTwo = sendChannel(owner, channelId, "relink-2-" + nanos(), "second message");

    MvcResult uploaded =
        mockMvc
            .perform(
                multipart("/api/v1/uploads")
                    .file(
                        new MockMultipartFile(
                            "file", "relink.txt", "text/plain", "relink file".getBytes()))
                    .param("channelId", channelId)
                    .headers(auth(owner)))
            .andExpect(status().isCreated())
            .andReturn();
    String attachmentId =
        objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/messages/" + messageOne + "/attachments")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentIds\":[\"" + attachmentId + "\"]}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/messages/" + messageTwo + "/attachments")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentIds\":[\"" + attachmentId + "\"]}"))
        .andExpect(status().isConflict());
  }

  @Test
  void finalizeToDeletedMessageRejected() throws Exception {
    Session owner = register("atdeletedowner" + nanos());
    String communityId = createCommunity(owner, "Attachment Deleted Gate");
    String channelId = firstChannelId(owner, communityId);
    String messageId = sendChannel(owner, channelId, "deleted-msg-" + nanos(), "delete me");

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/v1/messages/" + messageId)
                .headers(auth(owner)))
        .andExpect(status().isNoContent());

    MvcResult uploaded =
        mockMvc
            .perform(
                multipart("/api/v1/uploads")
                    .file(
                        new MockMultipartFile(
                            "file", "deleted.txt", "text/plain", "deleted target".getBytes()))
                    .param("channelId", channelId)
                    .headers(auth(owner)))
            .andExpect(status().isCreated())
            .andReturn();
    String attachmentId =
        objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/messages/" + messageId + "/attachments")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentIds\":[\"" + attachmentId + "\"]}"))
        .andExpect(status().isConflict());
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
    return new Session(node.get("user").get("id").asText(), node.get("accessToken").asText());
  }

  private String createCommunity(Session owner, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/communities")
                    .headers(auth(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private void join(Session owner, Session member, String communityId) throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/communities/" + communityId + "/invites")
                    .headers(auth(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isCreated())
            .andReturn();
    String code = objectMapper.readTree(created.getResponse().getContentAsString()).get("code").asText();
    mockMvc
        .perform(post("/api/v1/invites/" + code + "/join").headers(auth(member)))
        .andExpect(status().isOk());
  }

  private String firstChannelId(Session owner, String communityId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/communities/" + communityId + "/channels").headers(auth(owner)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get(0).get("id").asText();
  }

  private String sendChannel(Session sender, String channelId, String clientId, String content)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/channels/" + channelId + "/messages")
                    .headers(auth(sender))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"clientMessageId\":\"" + clientId + "\",\"content\":\"" + content + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private org.springframework.http.HttpHeaders auth(Session session) {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + session.token());
    return headers;
  }

  private String nanos() {
    return java.util.UUID.randomUUID().toString().substring(0, 8).replace("-", "a");
  }

  private record Session(String userId, String token) {}
}
