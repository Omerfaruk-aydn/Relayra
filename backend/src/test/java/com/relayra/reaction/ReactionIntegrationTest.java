package com.relayra.reaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
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
class ReactionIntegrationTest {

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
  void addIsIdempotentAndRemoveIsIdempotent() throws Exception {
    ChannelContext context = channelContext("rxflow");
    String messageId = sendChannel(context.member(), context.channelId(), "rx-1", "react me");

    mockMvc
        .perform(
            put("/api/v1/messages/{messageId}/reactions/{emoji}", messageId, "👍")
                .headers(auth(context.member())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].emoji").value("👍"))
        .andExpect(jsonPath("$[0].count").value(1))
        .andExpect(jsonPath("$[0].mine").value(true));
    mockMvc
        .perform(
            put("/api/v1/messages/{messageId}/reactions/{emoji}", messageId, "👍")
                .headers(auth(context.member())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].count").value(1));

    mockMvc
        .perform(delete("/api/v1/messages/{messageId}/reactions/{emoji}", messageId, "👍")
                .headers(auth(context.member())))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(delete("/api/v1/messages/{messageId}/reactions/{emoji}", messageId, "👍")
                .headers(auth(context.member())))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/v1/messages/" + messageId + "/reactions").headers(auth(context.member())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    mockMvc
        .perform(
            put("/api/v1/messages/{messageId}/reactions/{emoji}", messageId, "🔥")
                .headers(auth(context.owner())))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/messages/" + messageId + "/reactions").headers(auth(context.member())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].mine").value(false));
  }

  @Test
  void deletedMessageRejectsReactionAndScopeIsEnforced() throws Exception {
    ChannelContext context = channelContext("rxscope");
    Session outsider = register("rxoutside" + nanos());
    String messageId = sendChannel(context.member(), context.channelId(), "rx-2", "scope me");

    mockMvc
        .perform(
            put("/api/v1/messages/{messageId}/reactions/{emoji}", messageId, "👍")
                .headers(auth(outsider)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/channels/00000000-0000-0000-0000-000000000000/messages")
                .headers(auth(outsider)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(delete("/api/v1/messages/" + messageId).headers(auth(context.member())))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            put("/api/v1/messages/{messageId}/reactions/{emoji}", messageId, "👍")
                .headers(auth(context.member())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MESSAGE_DELETED"));

    ConversationContext dm = dmContext("rxdm");
    String dmMessage = sendDm(dm.first(), dm.conversationId(), "rx-dm-1", "dm react");
    mockMvc
        .perform(
            put("/api/v1/messages/{messageId}/reactions/{emoji}", dmMessage, "❤")
                .headers(auth(dm.second())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].count").value(1));
    Session dmOutsider = register("rxdmout" + nanos());
    mockMvc
        .perform(
            put("/api/v1/messages/{messageId}/reactions/{emoji}", dmMessage, "❤")
                .headers(auth(dmOutsider)))
        .andExpect(status().isNotFound());
  }

  @Test
  void concurrentDuplicateAddCreatesSingleReaction() throws Exception {
    ChannelContext context = channelContext("rxrace");
    String messageId = sendChannel(context.member(), context.channelId(), "rx-3", "race me");
    int threads = 6;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> futures = new ArrayList<>();
    for (int index = 0; index < threads; index++) {
      futures.add(
          pool.submit(
              () -> {
                ready.countDown();
                start.await();
                MvcResult result =
                    mockMvc
                        .perform(
                            put("/api/v1/messages/{messageId}/reactions/{emoji}", messageId, "🚀")
                                .headers(auth(context.member())))
                        .andReturn();
                if (result.getResponse().getStatus() != 200) {
                  throw new AssertionError(
                      "Unexpected reaction status " + result.getResponse().getStatus());
                }
                return objectMapper
                    .readTree(result.getResponse().getContentAsString())
                    .get(0)
                    .get("count")
                    .asInt();
              }));
    }
    ready.await();
    start.countDown();
    for (Future<Integer> future : futures) {
      if (future.get() != 1) {
        throw new AssertionError("Concurrent reaction duplicated.");
      }
    }
    pool.shutdown();
  }

  @Test
  void reactionBroadcastsOnChannelAndDmPaths() throws Exception {
    ChannelContext context = channelContext("rxws");
    String messageId = sendChannel(context.member(), context.channelId(), "rx-4", "broadcast me");

    AtomicReference<JsonNode> channelEvent = new AtomicReference<>();
    CountDownLatch channelLatch =
        subscribe(
            connect(context.owner().token()),
            "/topic/channels/" + context.channelId() + "/messages",
            channelEvent,
            "REACTION_ADDED");
    mockMvc
        .perform(
            put("/api/v1/messages/{messageId}/reactions/{emoji}", messageId, "👀")
                .headers(auth(context.member())))
        .andExpect(status().isOk());
    if (!channelLatch.await(5, TimeUnit.SECONDS)) {
      throw new AssertionError("Channel reaction was not broadcast.");
    }
    if (!messageId.equals(channelEvent.get().get("data").get("messageId").asText())) {
      throw new AssertionError("Unexpected channel reaction envelope: " + channelEvent.get());
    }

    ConversationContext dm = dmContext("rxwsdm");
    String dmMessage = sendDm(dm.first(), dm.conversationId(), "rx-ws-1", "dm broadcast");
    AtomicReference<JsonNode> dmEvent = new AtomicReference<>();
    CountDownLatch dmLatch =
        subscribe(connect(dm.second().token()), "/user/queue/messages", dmEvent, "REACTION_ADDED");
    AtomicReference<JsonNode> dmOutsiderEvent = new AtomicReference<>();
    CountDownLatch dmOutsiderLatch =
        subscribe(
            connect(register("rxwsout" + nanos()).token()),
            "/user/queue/messages",
            dmOutsiderEvent,
            "REACTION_ADDED");
    mockMvc
        .perform(
            put("/api/v1/messages/{messageId}/reactions/{emoji}", dmMessage, "🎉")
                .headers(auth(dm.first())))
        .andExpect(status().isOk());
    if (!dmLatch.await(5, TimeUnit.SECONDS)) {
      throw new AssertionError("DM reaction was not delivered to participant.");
    }
    if (dmOutsiderLatch.await(500, TimeUnit.MILLISECONDS) || dmOutsiderEvent.get() != null) {
      throw new AssertionError("DM reaction leaked to non-participant.");
    }

    AtomicReference<JsonNode> removed = new AtomicReference<>();
    CountDownLatch removedLatch =
        subscribe(
            connect(context.member().token()),
            "/topic/channels/" + context.channelId() + "/messages",
            removed,
            "REACTION_REMOVED");
    mockMvc
        .perform(delete("/api/v1/messages/{messageId}/reactions/{emoji}", messageId, "👀")
                .headers(auth(context.member())))
        .andExpect(status().isNoContent());
    if (!removedLatch.await(5, TimeUnit.SECONDS)) {
      throw new AssertionError("Reaction removal was not broadcast.");
    }
  }

  private ChannelContext channelContext(String prefix) throws Exception {
    Session owner = register(prefix + "owner" + nanos());
    Session member = register(prefix + "member" + nanos());
    String communityId = createCommunity(owner, prefix + " community");
    join(owner, member, communityId);
    return new ChannelContext(owner, member, communityId, firstChannelId(owner, communityId));
  }

  private ConversationContext dmContext(String prefix) throws Exception {
    Session first = register(prefix + "a" + nanos());
    Session second = register(prefix + "b" + nanos());
    MvcResult result =
        mockMvc
            .perform(post("/api/v1/conversations/direct/" + second.userId()).headers(auth(first)))
            .andExpect(status().isCreated())
            .andReturn();
    String conversationId =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    return new ConversationContext(first, second, conversationId);
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
      StompSession stompSession,
      String destination,
      AtomicReference<JsonNode> value,
      String expectedType) {
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
              if (expectedType.equals(event.get("type").asText())) {
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

  private record Session(String userId, String token) {}

  private record ChannelContext(Session owner, Session member, String communityId, String channelId) {}

  private record ConversationContext(Session first, Session second, String conversationId) {}
}
