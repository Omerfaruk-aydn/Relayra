package com.relayra.attachment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttachmentIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void uploadFinalizeDownloadRoundTrip() throws Exception {
    Session owner = register("atowner" + nanos());
    Session member = register("atmember" + nanos());
    String communityId = createCommunity(owner, "Attachment Net");
    join(owner, member, communityId);
    String channelId = firstChannelId(owner, communityId);
    String messageId = sendChannel(member, channelId, "at-1", "file below");

    MvcResult uploaded =
        mockMvc
            .perform(
                multipart("/api/v1/uploads")
                    .file(
                        new MockMultipartFile(
                            "file", "hello.txt", "text/plain", "hello relayra".getBytes()))
                    .param("channelId", channelId)
                    .headers(auth(member)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mimeType").value("text/plain"))
            .andReturn();
    String attachmentId =
        objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/messages/" + messageId + "/attachments")
                .headers(auth(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentIds\":[\"" + attachmentId + "\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].messageId").value(messageId));

    mockMvc
        .perform(get("/api/v1/attachments/" + attachmentId).headers(auth(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messageId").value(messageId));

    mockMvc
        .perform(get("/api/v1/attachments/" + attachmentId + "/download").headers(auth(owner)))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("hello.txt")));

    Session outsider = register("atoutsider" + nanos());
    mockMvc
        .perform(get("/api/v1/attachments/" + attachmentId + "/download").headers(auth(outsider)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                "/api/v1/messages/" + messageId)
            .headers(auth(member)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/v1/attachments/" + attachmentId + "/download").headers(auth(owner)))
        .andExpect(status().is(409));
  }

  @Test
  void validationBlocksSpoofOversizeAndExecutable() throws Exception {
    Session owner = register("atsecowner" + nanos());
    String communityId = createCommunity(owner, "Attachment Gate");
    String channelId = firstChannelId(owner, communityId);

    mockMvc
        .perform(
            multipart("/api/v1/uploads")
                .file(new MockMultipartFile("file", "evil.exe", "text/plain", "MZfake".getBytes()))
                .param("channelId", channelId)
                .headers(auth(owner)))
        .andExpect(status().isUnsupportedMediaType());

    byte[] pngHeader = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01};
    mockMvc
        .perform(
            multipart("/api/v1/uploads")
                .file(new MockMultipartFile("file", "spoof.txt", "text/plain", pngHeader))
                .param("channelId", channelId)
                .headers(auth(owner)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.mimeType").value("image/png"));

    byte[] big = new byte[(int) (10L * 1024 * 1024 + 1)];
    mockMvc
        .perform(
            multipart("/api/v1/uploads")
                .file(new MockMultipartFile("file", "big.bin", "application/octet-stream", big))
                .param("channelId", channelId)
                .headers(auth(owner)))
        .andExpect(status().isPayloadTooLarge());
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
    return String.valueOf(Math.abs(System.nanoTime() % 100000));
  }

  private record Session(String userId, String token) {}
}
