package com.relayra.conversation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class ConversationIntegrationTest {

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
  void directIsCanonicalAndListsParticipants() throws Exception {
    Session first = register("dmfirst" + nanos());
    Session second = register("dmsecond" + nanos());
    String firstId = direct(first, second.userId());
    String secondId = direct(second, first.userId());
    if (!firstId.equals(secondId)) {
      throw new AssertionError("Direct conversation is not canonical.");
    }
    mockMvc
        .perform(get("/api/v1/conversations").headers(auth(first)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(firstId))
        .andExpect(jsonPath("$[0].participants.length()").value(2));
    mockMvc
        .perform(get("/api/v1/conversations/" + firstId).headers(auth(first)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstId));
    mockMvc
        .perform(post("/api/v1/conversations/direct/" + first.userId()).headers(auth(first)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void outsiderCannotReadOrSendAndBlockedPeerCannotSend() throws Exception {
    Session first = register("dmblocka" + nanos());
    Session second = register("dmblockb" + nanos());
    Session outsider = register("dmblockc" + nanos());
    String conversationId = direct(first, second.userId());

    mockMvc
        .perform(get("/api/v1/conversations/" + conversationId).headers(auth(outsider)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/v1/conversations/" + conversationId + "/messages")
                .headers(auth(outsider))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientMessageId\":\"dm-x\",\"content\":\"hi\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/v1/conversations/" + conversationId + "/messages").headers(auth(outsider)))
        .andExpect(status().isNotFound());

    String messageId = sendDm(first, conversationId, "dm-1", "hello");
    mockMvc
        .perform(
            get("/api/v1/conversations/" + conversationId + "/messages").headers(auth(second)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages.length()").value(1))
        .andExpect(jsonPath("$.messages[0].id").value(messageId));

    block(second, first.userId());
    mockMvc
        .perform(
            post("/api/v1/conversations/" + conversationId + "/messages")
                .headers(auth(first))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientMessageId\":\"dm-2\",\"content\":\"after block\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
    mockMvc
        .perform(
            post("/api/v1/conversations/direct/" + first.userId()).headers(auth(second)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/v1/messages/" + messageId)
                .headers(auth(first))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"edited after block\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/v1/messages/" + messageId)
                .headers(auth(first)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/messages/{messageId}/reactions/{emoji}", messageId, "👍")
                .headers(auth(first)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
    mockMvc
        .perform(get("/api/v1/messages/" + messageId + "/reactions").headers(auth(first)))
        .andExpect(status().isOk());
  }

  @Test
  void concurrentDirectCreatesSingleConversation() throws Exception {
    Session first = register("dmracea" + nanos());
    Session second = register("dmraceb" + nanos());
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
                            post("/api/v1/conversations/direct/" + second.userId())
                                .headers(auth(first)))
                        .andReturn();
                if (result.getResponse().getStatus() != 201) {
                  throw new AssertionError(
                      "Unexpected direct status " + result.getResponse().getStatus());
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
      throw new AssertionError("Concurrent direct created duplicate conversations.");
    }
  }

  @Test
  void dmDeliveredPrivatelyOverWebSocket() throws Exception {
    Session first = register("dmwsa" + nanos());
    Session second = register("dmwsb" + nanos());
    Session outsider = register("dmwsc" + nanos());
    String conversationId = direct(first, second.userId());

    AtomicReference<JsonNode> received = new AtomicReference<>();
    CountDownLatch receivedLatch =
        subscribe(connect(second.token()), "/user/queue/messages", received);
    AtomicReference<JsonNode> outsiderReceived = new AtomicReference<>();
    CountDownLatch outsiderLatch =
        subscribe(connect(outsider.token()), "/user/queue/messages", outsiderReceived);
    AtomicReference<JsonNode> ack = new AtomicReference<>();
    CountDownLatch ackLatch = subscribe(connect(first.token()), "/user/queue/acks", ack);

    StompSession sender = connect(first.token());
    try {
      StompHeaders headers = new StompHeaders();
      headers.setDestination("/app/conversations/" + conversationId + "/messages");
      headers.setContentType(MediaType.APPLICATION_JSON);
      sender.send(
          headers,
          "{\"clientMessageId\":\"dm-ws-1\",\"content\":\"private hello\"}"
              .getBytes(StandardCharsets.UTF_8));
      if (!ackLatch.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("DM send was not acknowledged.");
      }
      if (!"MESSAGE_ACK".equals(ack.get().get("type").asText())) {
        throw new AssertionError("Unexpected DM ack envelope: " + ack.get());
      }
      if (!receivedLatch.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("DM was not delivered to participant.");
      }
      if (!"MESSAGE_CREATED".equals(received.get().get("type").asText())
          || !conversationId.equals(received.get().get("data").get("conversationId").asText())) {
        throw new AssertionError("Unexpected DM envelope: " + received.get());
      }
      if (outsiderLatch.await(500, TimeUnit.MILLISECONDS) || outsiderReceived.get() != null) {
        throw new AssertionError("DM leaked to non-participant.");
      }
      StompSession intruder = connect(outsider.token());
      try {
        StompHeaders intruderHeaders = new StompHeaders();
        intruderHeaders.setDestination("/app/conversations/" + conversationId + "/messages");
        intruderHeaders.setContentType(MediaType.APPLICATION_JSON);
        intruder.send(
            intruderHeaders,
            "{\"clientMessageId\":\"dm-ws-x\",\"content\":\"intrude\"}"
                .getBytes(StandardCharsets.UTF_8));
        Thread.sleep(500);
        if (intruder.isConnected()) {
          throw new AssertionError("Unauthorized DM send did not close the STOMP session.");
        }
      } finally {
        disconnectQuietly(intruder);
      }
    } finally {
      disconnectQuietly(sender);
    }
    mockMvc
        .perform(
            get("/api/v1/conversations/" + conversationId + "/messages").headers(auth(first)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages.length()").value(1));
  }

  private String direct(Session caller, String peerUserId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(post("/api/v1/conversations/direct/" + peerUserId).headers(auth(caller)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private String sendDm(Session sender, String conversationId, String clientId, String content)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/conversations/" + conversationId + "/messages")
                    .headers(auth(sender))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"clientMessageId\":\"" + clientId + "\",\"content\":\"" + content + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private void block(Session blocker, String targetUserId) throws Exception {
    mockMvc
        .perform(post("/api/v1/users/" + targetUserId + "/block").headers(auth(blocker)))
        .andExpect(status().isNoContent());
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

  private CountDownLatch subscribe(
      StompSession stompSession, String destination, AtomicReference<JsonNode> value) {
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
              value.set(objectMapper.readTree((byte[]) payload));
              latch.countDown();
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

  private record Session(String userId, String token) {}
}
