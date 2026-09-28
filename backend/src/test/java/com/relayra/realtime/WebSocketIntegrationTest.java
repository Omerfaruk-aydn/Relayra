package com.relayra.realtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
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
class WebSocketIntegrationTest {

  @LocalServerPort private int port;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private WebSocketStompClient stompClient;
  private StompSession session;

  @AfterEach
  void cleanup() {
    if (session != null && session.isConnected()) {
      session.disconnect();
    }
    if (stompClient != null) {
      stompClient.stop();
    }
  }

  @Test
  void authenticatedMemberCanSubscribeSendReceiveAndAck() throws Exception {
    Context context = context("wsflow");
    session = connect(context.member().token());
    AtomicReference<JsonNode> topicEvent = new AtomicReference<>();
    AtomicReference<JsonNode> ackEvent = new AtomicReference<>();
    CountDownLatch topicLatch = subscribe(
        session,
        "/topic/channels/" + context.channelId() + "/messages",
        topicEvent);
    CountDownLatch ackLatch = subscribe(session, "/user/queue/acks", ackEvent);

    StompHeaders headers = new StompHeaders();
    headers.setDestination("/app/channels/" + context.channelId() + "/messages");
    headers.setContentType(MediaType.APPLICATION_JSON);
    session.send(
        headers,
        "{\"clientMessageId\":\"ws-flow-1\",\"content\":\"WebSocket hello\"}"
            .getBytes(StandardCharsets.UTF_8));

    if (!topicLatch.await(5, TimeUnit.SECONDS) || !ackLatch.await(5, TimeUnit.SECONDS)) {
      throw new AssertionError("Expected WebSocket broadcast and acknowledgement.");
    }
    if (!"MESSAGE_CREATED".equals(topicEvent.get().get("type").asText())
        || !"WebSocket hello".equals(topicEvent.get().get("data").get("content").asText())
        || !"MESSAGE_ACK".equals(ackEvent.get().get("type").asText())) {
      throw new AssertionError("Unexpected realtime event envelope.");
    }
  }

  @Test
  void invalidTokenCannotConnect() throws Exception {
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt");
    CompletableFuture<StompSession> future =
        stompClient.connectAsync(
            url(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {});
    try {
      future.get(5, TimeUnit.SECONDS);
      throw new AssertionError("Invalid token unexpectedly connected.");
    } catch (java.util.concurrent.ExecutionException expected) {
      if (expected.getCause() == null) {
        throw expected;
      }
    }
  }

  @Test
  void outsiderCannotSubscribeToCommunityChannel() throws Exception {
    Context context = context("wsauthz");
    Session outsider = register("wsoutsider" + nanos());
    session = connect(outsider.token());
    session.subscribe(
        "/topic/channels/" + context.channelId() + "/messages",
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {}
        });
    Thread.sleep(300);
    if (session.isConnected()) {
      session.send("/app/unsupported", new byte[0]);
      Thread.sleep(300);
    }
    if (session.isConnected()) {
      throw new AssertionError("Unauthorized subscription did not close the STOMP session.");
    }
  }

  @Test
  void malformedSendReturnsSafeUserError() throws Exception {
    Context context = context("wserror");
    session = connect(context.member().token());
    AtomicReference<JsonNode> errorEvent = new AtomicReference<>();
    CountDownLatch errorLatch = subscribe(session, "/user/queue/errors", errorEvent);
    StompHeaders headers = new StompHeaders();
    headers.setDestination("/app/channels/" + context.channelId() + "/messages");
    headers.setContentType(MediaType.APPLICATION_JSON);
    session.send(headers, "{\"content\":\"missing id\"}".getBytes(StandardCharsets.UTF_8));
    if (!errorLatch.await(5, TimeUnit.SECONDS)) {
      throw new AssertionError("Expected safe protocol error.");
    }
    JsonNode error = errorEvent.get();
    if (!"ERROR".equals(error.get("type").asText())
        || error.toString().contains("Exception")
        || error.toString().contains("stack")) {
      throw new AssertionError("Unsafe WebSocket error payload.");
    }
  }

  private StompSession connect(String token) throws Exception {
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    return stompClient
        .connectAsync(
            url(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
        .get(5, TimeUnit.SECONDS);
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
    return new Session(node.get("accessToken").asText());
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

  private record Session(String token) {}

  private record Context(
      Session owner, Session member, String communityId, String channelId) {}
}
