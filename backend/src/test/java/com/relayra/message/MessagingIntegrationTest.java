package com.relayra.message;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class MessagingIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void sendHistoryEditAndSoftDeleteFlow() throws Exception {
    Context context = context("msgflow");
    String messageId = send(context.member(), context.channelId(), "flow-1", " hello world ", null);

    mockMvc
        .perform(
            get("/api/v1/channels/" + context.channelId() + "/messages")
                .headers(auth(context.member())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages.length()").value(1))
        .andExpect(jsonPath("$.messages[0].content").value("hello world"))
        .andExpect(jsonPath("$.messages[0].author.id").value(context.member().userId()));

    mockMvc
        .perform(
            patch("/api/v1/messages/" + messageId)
                .headers(auth(context.member()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"edited text\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value("edited text"))
        .andExpect(jsonPath("$.editedAt").isNotEmpty());

    mockMvc
        .perform(delete("/api/v1/messages/" + messageId).headers(auth(context.member())))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            patch("/api/v1/messages/" + messageId)
                .headers(auth(context.member()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"resurrect\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MESSAGE_DELETED"));
  }

  @Test
  void outsiderCannotReadAndMemberCannotEditOthersMessage() throws Exception {
    Context context = context("msgauth");
    Session outsider = register("msgoutside" + nanos());
    String messageId = send(context.owner(), context.channelId(), "auth-1", "owner text", null);

    mockMvc
        .perform(
            get("/api/v1/channels/" + context.channelId() + "/messages")
                .headers(auth(outsider)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/v1/messages/" + messageId)
                .headers(auth(context.member()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hacked\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/v1/messages/" + messageId).headers(auth(context.member())))
        .andExpect(status().isForbidden());
  }

  @Test
  void moderatorCanDeleteButCannotEditOthersMessage() throws Exception {
    Context context = context("msgmod");
    String roleId = createRole(context.owner(), context.communityId(), "Message Mod", "DELETE_MESSAGES");
    assign(context.owner(), context.communityId(), context.member().userId(), roleId);
    String messageId = send(context.owner(), context.channelId(), "mod-1", "moderate me", null);

    mockMvc
        .perform(
            patch("/api/v1/messages/" + messageId)
                .headers(auth(context.member()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"forbidden edit\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/v1/messages/" + messageId).headers(auth(context.member())))
        .andExpect(status().isNoContent());
  }

  @Test
  void replyMustStayInSameChannel() throws Exception {
    Context context = context("msgreply");
    String otherChannel = createChannel(context.owner(), context.communityId(), "other-channel");
    String target = send(context.member(), context.channelId(), "reply-1", "target", null);

    sendExpecting(context.member(), context.channelId(), "reply-2", "valid reply", target, 201);
    sendExpecting(context.member(), otherChannel, "reply-3", "cross reply", target, 400);
  }

  @Test
  void cursorPaginationIsStableAndBounded() throws Exception {
    Context context = context("msgpage");
    for (int index = 0; index < 5; index++) {
      send(context.member(), context.channelId(), "page-" + index, "message " + index, null);
    }
    MvcResult first =
        mockMvc
            .perform(
                get("/api/v1/channels/" + context.channelId() + "/messages?limit=2")
                    .headers(auth(context.member())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(2))
            .andReturn();
    JsonNode firstPage = objectMapper.readTree(first.getResponse().getContentAsString());
    String beforeTime = firstPage.get("nextBeforeCreatedAt").asText();
    String beforeId = firstPage.get("nextBeforeId").asText();
    MvcResult second =
        mockMvc
            .perform(
                get(
                        "/api/v1/channels/"
                            + context.channelId()
                            + "/messages?limit=2&beforeCreatedAt="
                            + beforeTime
                            + "&beforeId="
                            + beforeId)
                    .headers(auth(context.member())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(2))
            .andReturn();
    Set<String> ids = new HashSet<>();
    firstPage.get("messages").forEach(node -> ids.add(node.get("id").asText()));
    objectMapper
        .readTree(second.getResponse().getContentAsString())
        .get("messages")
        .forEach(node -> ids.add(node.get("id").asText()));
    if (ids.size() != 4) {
      throw new AssertionError("Cursor pages overlapped.");
    }
    mockMvc
        .perform(
            get("/api/v1/channels/" + context.channelId() + "/messages?limit=101")
                .headers(auth(context.member())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void duplicateClientMessageIdIsIdempotentIncludingConcurrentSends() throws Exception {
    Context context = context("msgidem");
    String first = send(context.member(), context.channelId(), "same-id", "same text", null);
    String duplicate = send(context.member(), context.channelId(), "same-id", "same text", null);
    if (!first.equals(duplicate)) {
      throw new AssertionError("Sequential retry created a duplicate message.");
    }

    int threads = 6;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<String>> futures = new ArrayList<>();
    for (int index = 0; index < threads; index++) {
      futures.add(
          pool.submit(
              () -> {
                ready.countDown();
                start.await();
                MvcResult result =
                    mockMvc
                        .perform(
                            post("/api/v1/channels/" + context.channelId() + "/messages")
                                .headers(auth(context.member()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    "{\"clientMessageId\":\"race-id\",\"content\":\"race text\"}"))
                        .andReturn();
                if (result.getResponse().getStatus() != 201) {
                  throw new AssertionError("Unexpected send status " + result.getResponse().getStatus());
                }
                return objectMapper
                    .readTree(result.getResponse().getContentAsString())
                    .get("id")
                    .asText();
              }));
    }
    ready.await();
    start.countDown();
    Set<String> ids = new HashSet<>();
    for (Future<String> future : futures) {
      ids.add(future.get());
    }
    pool.shutdown();
    if (ids.size() != 1) {
      throw new AssertionError("Concurrent retry created duplicate messages.");
    }
  }

  @Test
  void contentAndCursorValidationRejectMalformedRequests() throws Exception {
    Context context = context("msgvalid");
    sendExpecting(context.member(), context.channelId(), "blank", "   ", null, 400);
    sendExpecting(
        context.member(), context.channelId(), "long", "x".repeat(4001), null, 400);
    mockMvc
        .perform(
            get("/api/v1/channels/" + context.channelId() + "/messages?beforeId=" + java.util.UUID.randomUUID())
                .headers(auth(context.member())))
        .andExpect(status().isBadRequest());
  }

  private Context context(String prefix) throws Exception {
    Session owner = register(prefix + "owner" + nanos());
    Session member = register(prefix + "member" + nanos());
    String communityId = createCommunity(owner, prefix + " community");
    join(owner, member, communityId);
    String channelId = firstChannelId(owner, communityId);
    return new Context(owner, member, communityId, channelId);
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

  private String firstChannelId(Session owner, String communityId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/communities/" + communityId + "/channels").headers(auth(owner)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get(0).get("id").asText();
  }

  private String createChannel(Session owner, String communityId, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/communities/" + communityId + "/channels")
                    .headers(auth(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\",\"type\":\"TEXT\"}"))
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

  private String createRole(Session owner, String communityId, String name, String permission)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/communities/" + communityId + "/roles")
                    .headers(auth(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\""
                            + name
                            + "\",\"permissions\":[\""
                            + permission
                            + "\"]}"))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private void assign(Session owner, String communityId, String userId, String roleId)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/members/" + userId + "/roles")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleId\":\"" + roleId + "\"}"))
        .andExpect(status().isOk());
  }

  private String send(
      Session sender, String channelId, String clientId, String content, String replyTo)
      throws Exception {
    MvcResult result = sendExpecting(sender, channelId, clientId, content, replyTo, 201);
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private MvcResult sendExpecting(
      Session sender,
      String channelId,
      String clientId,
      String content,
      String replyTo,
      int expectedStatus)
      throws Exception {
    java.util.Map<String, Object> body = new java.util.HashMap<>();
    body.put("clientMessageId", clientId);
    body.put("content", content);
    if (replyTo != null) {
      body.put("replyToMessageId", replyTo);
    }
    return mockMvc
        .perform(
            post("/api/v1/channels/" + channelId + "/messages")
                .headers(auth(sender))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().is(expectedStatus))
        .andReturn();
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

  private record Context(
      Session owner, Session member, String communityId, String channelId) {}
}
