package com.relayra.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

  @LocalServerPort private int port;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private final List<WebSocketStompClient> stompClients = new ArrayList<>();
  private final List<StompSession> stompSessions = new ArrayList<>();

  @AfterEach
  void cleanup() {
    stompSessions.forEach(this::disconnectQuietly);
    stompSessions.clear();
    stompClients.forEach(WebSocketStompClient::stop);
    stompClients.clear();
  }

  @Test
  void mentionCreatesNotificationAndPrivatePush() throws Exception {
    Session owner = register("ntowner" + nanos());
    Session mentioned = register("ntmentioned" + nanos());
    String communityId = createCommunity(owner, "Mention Net");
    join(owner, mentioned, communityId);
    String channelId = firstChannelId(owner, communityId);

    AtomicReference<JsonNode> event = new AtomicReference<>();
    CountDownLatch latch =
        subscribe(connect(mentioned.token()), "/user/queue/notifications", event);

    sendChannel(
        owner,
        channelId,
        "nt-1",
        "hello @" + mentioned.username() + " welcome");
    if (!latch.await(5, TimeUnit.SECONDS)) {
      throw new AssertionError("Mention notification was not pushed.");
    }
    if (!"MENTION".equals(event.get().get("data").get("type").asText())) {
      throw new AssertionError("Unexpected notification push: " + event.get());
    }

    MvcResult listed =
        mockMvc
            .perform(get("/api/v1/notifications").headers(auth(mentioned)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notifications.length()").value(1))
            .andExpect(jsonPath("$.notifications[0].type").value("MENTION"))
            .andExpect(jsonPath("$.unreadCount").value(1))
            .andReturn();
    String notificationId =
        objectMapper
            .readTree(listed.getResponse().getContentAsString())
            .get("notifications")
            .get(0)
            .get("id")
            .asText();

    mockMvc
        .perform(patch("/api/v1/notifications/" + notificationId + "/read").headers(auth(mentioned)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readAt").exists());
    mockMvc
        .perform(get("/api/v1/notifications").headers(auth(mentioned)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(0));
  }

  @Test
  void friendRequestReactionAndModerationCreateNotifications() throws Exception {
    Session sender = register("ntfrienda" + nanos());
    Session receiver = register("ntfriendb" + nanos());
    MvcResult requestResult =
        mockMvc
            .perform(
                post("/api/v1/friends/requests")
                    .headers(auth(sender))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"receiverId\":\"" + receiver.userId() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String requestId =
        objectMapper.readTree(requestResult.getResponse().getContentAsString()).get("id").asText();
    if (requestId == null) {
      throw new AssertionError("Friend request must exist.");
    }
    mockMvc
        .perform(get("/api/v1/notifications").headers(auth(receiver)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notifications[?(@.type == 'FRIEND_REQUEST')]").exists());

    Session owner = register("ntreactowner" + nanos());
    Session member = register("ntreactmember" + nanos());
    String communityId = createCommunity(owner, "Reaction Net");
    join(owner, member, communityId);
    String channelId = firstChannelId(owner, communityId);
    String messageId = sendChannel(member, channelId, "nt-2", "react target");
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/messages/{messageId}/reactions/{emoji}", messageId, "👍")
                .headers(auth(owner)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/notifications").headers(auth(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notifications[?(@.type == 'REACTION')]").exists());

    Session target = register("ntmodtarget" + nanos());
    join(owner, target, communityId);
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/bans")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + target.userId() + "\"}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/api/v1/notifications").headers(auth(target)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notifications[?(@.type == 'MODERATION')]").exists());
  }

  @Test
  void notificationsArePrivateAndReadAllWorks() throws Exception {
    Session first = register("ntpriva" + nanos());
    Session second = register("ntprivb" + nanos());
    Session outsider = register("ntprivc" + nanos());

    mockMvc
        .perform(
            post("/api/v1/friends/requests")
                .headers(auth(first))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"" + second.userId() + "\"}"))
        .andExpect(status().isCreated());

    MvcResult listed =
        mockMvc
            .perform(get("/api/v1/notifications").headers(auth(second)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notifications.length()").value(1))
            .andReturn();
    String notificationId =
        objectMapper
            .readTree(listed.getResponse().getContentAsString())
            .get("notifications")
            .get(0)
            .get("id")
            .asText();

    mockMvc
        .perform(patch("/api/v1/notifications/" + notificationId + "/read").headers(auth(outsider)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(patch("/api/v1/notifications/" + notificationId + "/read").headers(auth(first)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/notifications").headers(auth(outsider)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notifications.length()").value(0));

    mockMvc
        .perform(post("/api/v1/notifications/read-all").headers(auth(second)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/v1/notifications").headers(auth(second)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(0));
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

  private StompSession connect(String token) throws Exception {
    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    stompClients.add(client);
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    StompSession created =
        client
            .connectAsync(
                url(),
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
    stompSessions.add(created);
    return created;
  }

  private CountDownLatch subscribe(StompSession stompSession, String destination, AtomicReference<JsonNode> value) {
    CountDownLatch latch = new CountDownLatch(1);
    stompSession.subscribe(
        destination,
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            try {
              JsonNode event = objectMapper.readTree((byte[]) payload);
              if ("NOTIFICATION_CREATED".equals(event.get("type").asText())) {
                value.set(event);
                latch.countDown();
              }
            } catch (Exception exception) {
              throw new AssertionError(exception);
            }
          }
        });
    return latch;
  }

  private void disconnectQuietly(StompSession target) {
    try {
      if (target != null && target.isConnected()) {
        target.disconnect();
      }
    } catch (Exception ignored) {
    }
  }

  private org.springframework.http.HttpHeaders auth(Session session) {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + session.token());
    return headers;
  }

  private String url() {
    return "ws://localhost:" + port + "/ws";
  }

  private String nanos() {
    return String.valueOf(Math.abs(System.nanoTime() % 100000));
  }

  private record Session(String userId, String username, String token) {}
}
