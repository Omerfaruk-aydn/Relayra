package com.relayra.channel;

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
class ChannelIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void communityStartsWithGeneralChannel() throws Exception {
    Session owner = register("defchan" + nanos());
    String communityId = createCommunity(owner, "Default Channel Club");

    mockMvc
        .perform(get("/api/v1/communities/" + communityId + "/channels").headers(auth(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("general"))
        .andExpect(jsonPath("$[0].type").value("TEXT"))
        .andExpect(jsonPath("$[0].position").value(0));
  }

  @Test
  void createUpdateDeleteKeepsDensePositions() throws Exception {
    Session owner = register("crudchan" + nanos());
    String communityId = createCommunity(owner, "Channel CRUD Club");
    String designId = createChannel(owner, communityId, "design");
    String engineeringId = createChannel(owner, communityId, "engineering");

    mockMvc
        .perform(
            patch("/api/v1/channels/" + designId)
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"product-design\",\"description\":\"Design work\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("product-design"))
        .andExpect(jsonPath("$.description").value("Design work"));

    mockMvc
        .perform(delete("/api/v1/channels/" + designId).headers(auth(owner)))
        .andExpect(status().isNoContent());

    MvcResult listed = list(owner, communityId);
    JsonNode channels = objectMapper.readTree(listed.getResponse().getContentAsString());
    if (channels.size() != 2
        || channels.get(0).get("position").asInt() != 0
        || channels.get(1).get("position").asInt() != 1
        || !engineeringId.equals(channels.get(1).get("id").asText())) {
      throw new AssertionError("Channel positions were not compacted after delete.");
    }
  }

  @Test
  void memberCanListButCannotMutate() throws Exception {
    Session owner = register("permowner" + nanos());
    Session member = register("permmember" + nanos());
    Session outsider = register("permoutside" + nanos());
    String communityId = createCommunity(owner, "Channel Permissions Club");
    joinWithInvite(owner, member, communityId);

    mockMvc
        .perform(get("/api/v1/communities/" + communityId + "/channels").headers(auth(member)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/channels")
                .headers(auth(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"forbidden\",\"type\":\"TEXT\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/communities/" + communityId + "/channels").headers(auth(outsider)))
        .andExpect(status().isForbidden());
  }

  @Test
  void validationRejectsBadNamesDescriptionsAndTypes() throws Exception {
    Session owner = register("validchan" + nanos());
    String communityId = createCommunity(owner, "Channel Validation Club");

    createExpecting(owner, communityId, " ", "TEXT", 400);
    createExpecting(owner, communityId, "a", "TEXT", 400);
    createExpecting(owner, communityId, "valid", "VOICE", 400);
    String longName = "x".repeat(101);
    createExpecting(owner, communityId, longName, "TEXT", 400);
    String longDescription = "x".repeat(251);
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/channels")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        java.util.Map.of(
                            "name", "valid", "type", "TEXT", "description", longDescription))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void reorderPersistsExactOrderAndRejectsInvalidSets() throws Exception {
    Session owner = register("orderchan" + nanos());
    String communityId = createCommunity(owner, "Channel Order Club");
    String secondId = createChannel(owner, communityId, "second");
    String thirdId = createChannel(owner, communityId, "third");
    JsonNode initial =
        objectMapper.readTree(list(owner, communityId).getResponse().getContentAsString());
    String generalId = initial.get(0).get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/channels/reorder")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(channelIdsJson(thirdId, generalId, secondId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(thirdId))
        .andExpect(jsonPath("$[1].id").value(generalId))
        .andExpect(jsonPath("$[2].id").value(secondId));

    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/channels/reorder")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(channelIdsJson(generalId, secondId)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/channels/reorder")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(channelIdsJson(generalId, generalId, secondId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void cannotDeleteLastChannel() throws Exception {
    Session owner = register("lastchan" + nanos());
    String communityId = createCommunity(owner, "Last Channel Club");
    JsonNode listed =
        objectMapper.readTree(list(owner, communityId).getResponse().getContentAsString());

    mockMvc
        .perform(delete("/api/v1/channels/" + listed.get(0).get("id").asText()).headers(auth(owner)))
        .andExpect(status().isConflict());
  }

  @Test
  void concurrentCreatesAllocateUniqueDensePositions() throws Exception {
    Session owner = register("racechan" + nanos());
    String communityId = createCommunity(owner, "Channel Race Club");
    int threads = 6;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> futures = new ArrayList<>();
    for (int index = 0; index < threads; index++) {
      int number = index;
      futures.add(
          pool.submit(
              () -> {
                ready.countDown();
                start.await();
                return mockMvc
                    .perform(
                        post("/api/v1/communities/" + communityId + "/channels")
                            .headers(auth(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                "{\"name\":\"parallel-"
                                    + number
                                    + "\",\"type\":\"TEXT\"}"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              }));
    }
    ready.await();
    start.countDown();
    for (Future<Integer> future : futures) {
      if (future.get() != 201) {
        throw new AssertionError("Concurrent channel creation failed.");
      }
    }
    pool.shutdown();

    JsonNode listed =
        objectMapper.readTree(list(owner, communityId).getResponse().getContentAsString());
    Set<Integer> positions = new HashSet<>();
    for (JsonNode channel : listed) {
      positions.add(channel.get("position").asInt());
    }
    if (listed.size() != threads + 1 || positions.size() != threads + 1) {
      throw new AssertionError("Concurrent creates produced duplicate positions.");
    }
    for (int position = 0; position <= threads; position++) {
      if (!positions.contains(position)) {
        throw new AssertionError("Missing channel position " + position);
      }
    }
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

  private void createExpecting(
      Session owner, String communityId, String name, String type, int expectedStatus)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/channels")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        java.util.Map.of("name", name, "type", type))))
        .andExpect(status().is(expectedStatus));
  }

  private void joinWithInvite(Session owner, Session member, String communityId) throws Exception {
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

  private MvcResult list(Session session, String communityId) throws Exception {
    return mockMvc
        .perform(get("/api/v1/communities/" + communityId + "/channels").headers(auth(session)))
        .andExpect(status().isOk())
        .andReturn();
  }

  private String channelIdsJson(String... ids) throws Exception {
    return objectMapper.writeValueAsString(java.util.Map.of("channelIds", List.of(ids)));
  }

  private org.springframework.http.HttpHeaders auth(Session session) {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + session.token());
    return headers;
  }

  private String nanos() {
    return String.valueOf(Math.abs(System.nanoTime() % 100000));
  }

  private record Session(String token) {}
}
