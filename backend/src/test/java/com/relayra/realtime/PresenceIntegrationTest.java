package com.relayra.realtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
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
class PresenceIntegrationTest {

  @LocalServerPort private int port;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private PresenceService presence;

  private WebSocketStompClient stompClient;
  private StompSession session;
  private StompSession secondSession;

  @AfterEach
  void cleanup() {
    disconnectQuietly(session);
    disconnectQuietly(secondSession);
    session = null;
    secondSession = null;
    if (stompClient != null) {
      stompClient.stop();
      stompClient = null;
    }
  }

  @Test
  void connectMarksOnlineAndFriendsSeeStatus() throws Exception {
    Fixture fixture = fixture("presflow");
    StompSession ownerSession = connect(fixture.owner().token());
    StompSession memberSession = connect(fixture.member().token());
    try {
      presence.watchFriends(userId(fixture.owner()), java.util.Set.of(userId(fixture.member())));
      mockMvc
          .perform(get("/api/v1/presence").headers(auth(fixture.owner())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].userId").value(fixture.member().userId().toString()))
          .andExpect(jsonPath("$[0].status").value("ONLINE"));
    } finally {
      disconnectQuietly(ownerSession);
      disconnectQuietly(memberSession);
    }
  }

  @Test
  void typingSignalBroadcastsOnceWithServerClock() throws Exception {
    Fixture fixture = fixture("prestype");
    AtomicReference<JsonNode> typing = new AtomicReference<>();
    CountDownLatch latch =
        subscribe(
            sessionFor(fixture.member()),
            "/topic/channels/" + fixture.channelId() + "/typing",
            typing);
    sendTyping(fixture.member(), fixture.channelId(), true);
    if (!latch.await(5, TimeUnit.SECONDS)) {
      throw new AssertionError("Typing signal was not broadcast.");
    }
    JsonNode event = typing.get();
    if (!"TYPING".equals(event.get("type").asText())
        || !fixture.member().userId().toString().equals(event.get("data").get("userId").asText())
        || !event.get("data").get("typing").asBoolean()) {
      throw new AssertionError("Unexpected typing envelope.");
    }
    if (!presence.isTyping(userId(fixture.member()), fixture.channelId())) {
      throw new AssertionError("Typing TTL state was not recorded.");
    }
    sendTyping(fixture.member(), fixture.channelId(), false);
  }

  @Test
  void typingRequiresMembershipAndRespectsThrottle() throws Exception {
    Fixture fixture = fixture("presthro");
    Session outsider = register("presoutsider" + nanos());
    session = connect(outsider.token());
    StompSession memberSession = connect(fixture.member().token());
    try {
      AtomicReference<JsonNode> errorEvent = new AtomicReference<>();
      CountDownLatch errorLatch =
          subscribe(memberSession, "/user/queue/errors", errorEvent);
      sendTypingRaw(memberSession, fixture.channelId(), true);
      sendTypingRaw(outsider.token(), fixture.channelId(), true);
      Thread.sleep(500);
      if (presence.isTyping(userId(outsider), fixture.channelId())) {
        throw new AssertionError("Outsider typing was accepted.");
      }
      for (int index = 0; index < 61; index++) {
        sendTypingRaw(memberSession, fixture.channelId(), true);
      }
      if (!errorLatch.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("Typing throttle was not enforced.");
      }
      JsonNode error = errorEvent.get();
      if (!"ERROR".equals(error.get("type").asText())
          || !"RATE_LIMITED".equals(error.get("code").asText())) {
        throw new AssertionError("Unexpected typing throttle error: " + error);
      }
    } finally {
      disconnectQuietly(memberSession);
    }
  }

  @Test
  void typingStopBroadcastsFalseSignal() throws Exception {
    Fixture fixture = fixture("presstop");
    AtomicReference<JsonNode> typing = new AtomicReference<>();
    CountDownLatch latch =
        subscribe(
            sessionFor(fixture.member()),
            "/topic/channels/" + fixture.channelId() + "/typing",
            typing);
    sendTypingViaSession(sessionFor(fixture.member()), fixture.channelId(), true);
    if (!latch.await(5, TimeUnit.SECONDS)) {
      throw new AssertionError("Typing signal was not broadcast.");
    }
    AtomicReference<JsonNode> stopped = new AtomicReference<>();
    CountDownLatch stopLatch =
        subscribe(
            sessionFor(fixture.member()),
            "/topic/channels/" + fixture.channelId() + "/typing",
            stopped);
    sendTypingViaSession(sessionFor(fixture.member()), fixture.channelId(), false);
    if (!stopLatch.await(5, TimeUnit.SECONDS)) {
      throw new AssertionError("Typing stop was not broadcast.");
    }
    JsonNode event = stopped.get();
    if (!"TYPING".equals(event.get("type").asText())
        || event.get("data").get("typing").asBoolean()) {
      throw new AssertionError("Unexpected typing stop envelope: " + event);
    }
    if (presence.isTyping(userId(fixture.member()), fixture.channelId())) {
      throw new AssertionError("Typing state was not cleared on stop.");
    }
  }

  @Test
  void secondConnectionKeepsUserOnlineAfterFirstDisconnect() throws Exception {
    Fixture fixture = fixture("presmulti");
    session = connect(fixture.member().token());
    secondSession = connect(fixture.member().token());
    presence.watchFriends(userId(fixture.owner()), java.util.Set.of(userId(fixture.member())));
    disconnectQuietly(session);
    session = null;
    Thread.sleep(300);
    mockMvc
        .perform(get("/api/v1/presence").headers(auth(fixture.owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("ONLINE"));
  }

  private void sendTyping(Session sender, java.util.UUID channelId, boolean typing) {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    StompSession senderSession = null;
    try {
      senderSession = connect(sender.token());
      StompHeaders headers = new StompHeaders();
      headers.setDestination("/app/channels/" + channelId + "/typing");
      headers.setContentType(MediaType.APPLICATION_JSON);
      senderSession.send(
          headers,
          ("{\"typing\":" + typing + "}").getBytes(StandardCharsets.UTF_8));
    } catch (Exception exception) {
      failure.set(exception);
    } finally {
      disconnectQuietly(senderSession);
    }
    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
  }

  private void sendTypingRaw(String token, java.util.UUID channelId, boolean typing) throws Exception {
    StompSession raw = connect(token);
    try {
      sendTypingViaSession(raw, channelId, typing);
    } finally {
      disconnectQuietly(raw);
    }
  }

  private void sendTypingViaSession(StompSession target, java.util.UUID channelId, boolean typing) {
    StompHeaders headers = new StompHeaders();
    headers.setDestination("/app/channels/" + channelId + "/typing");
    headers.setContentType(MediaType.APPLICATION_JSON);
    target.send(headers, ("{\"typing\":" + typing + "}").getBytes(StandardCharsets.UTF_8));
  }

  private void sendTypingRaw(StompSession target, java.util.UUID channelId, boolean typing) {
    sendTypingViaSession(target, channelId, typing);
  }

  private StompSession sessionFor(Session token) throws Exception {
    if (session == null || !session.isConnected()) {
      session = connect(token.token());
    }
    return session;
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

  private StompSession connect(String token) throws Exception {
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    return stompClient
        .connectAsync(
            url(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
        .get(5, TimeUnit.SECONDS);
  }

  private void disconnectQuietly(StompSession target) {
    try {
      if (target != null && target.isConnected()) {
        target.disconnect();
      }
    } catch (Exception ignored) {
    }
  }

  private Fixture fixture(String prefix) throws Exception {
    String unique = prefix + nanos();
    Session owner = register(unique + "owner");
    Session member = register(unique + "member");
    java.util.UUID communityUuid = createCommunity(owner, unique + " community");
    join(owner, member, communityUuid);
    acceptFriendship(owner, member);
    String channelId = firstChannelId(owner, communityUuid);
    return new Fixture(
        owner, member, communityUuid, java.util.UUID.fromString(channelId));
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

  private java.util.UUID createCommunity(Session owner, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/communities")
                    .headers(auth(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return java.util.UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private void join(Session owner, Session member, java.util.UUID communityId) throws Exception {
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

  private void acceptFriendship(Session owner, Session member) throws Exception {
    MvcResult sent =
        mockMvc
            .perform(
                post("/api/v1/friends/requests")
                    .headers(auth(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"receiverId\":\"" + member.userId() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String requestId =
        objectMapper.readTree(sent.getResponse().getContentAsString()).get("id").asText();
    mockMvc
        .perform(post("/api/v1/friends/requests/" + requestId + "/accept").headers(auth(member)))
        .andExpect(status().isOk());
  }

  private String firstChannelId(Session owner, java.util.UUID communityId) throws Exception {
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

  private java.util.UUID userId(Session session) {
    return java.util.UUID.fromString(session.userId());
  }

  private String nanos() {
    return String.valueOf(Math.abs(System.nanoTime() % 100000));
  }

  private record Session(String userId, String token) {}

  private record Fixture(
      Session owner, Session member, java.util.UUID communityId, java.util.UUID channelId) {}
}
