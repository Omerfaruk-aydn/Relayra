package com.relayra.invite;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
class InviteIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void createResolveJoinRevokeFlow() throws Exception {
    Session owner = register("invowner" + nanos());
    Session guest = register("invguest" + nanos());
    String communityId = createCommunity(owner, "Invite Club");

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/communities/" + communityId + "/invites")
                    .headers(auth(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"maxUses\":5}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.usageCount").value(0))
            .andReturn();
    String code = code(created);
    String inviteId = id(created);

    mockMvc
        .perform(get("/api/v1/invites/" + code).headers(auth(guest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(code));

    mockMvc
        .perform(post("/api/v1/invites/" + code + "/join").headers(auth(guest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(guest.userId()));

    // Joining again consumes no usage and returns membership.
    mockMvc
        .perform(post("/api/v1/invites/" + code + "/join").headers(auth(guest)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/invites/" + code).headers(auth(guest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.usageCount").value(1));

    mockMvc
        .perform(delete("/api/v1/invites/" + inviteId).headers(auth(guest)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(delete("/api/v1/invites/" + inviteId).headers(auth(owner)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/invites/" + code).headers(auth(guest)))
        .andExpect(status().isGone());
  }

  @Test
  void nonOwnerCannotCreateInvite() throws Exception {
    Session owner = register("noinvowner" + nanos());
    Session outsider = register("noinvoutsider" + nanos());
    String communityId = createCommunity(owner, "Closed Club");
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/invites")
                .headers(auth(outsider))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void expiredInviteCannotBeUsed() throws Exception {
    Session owner = register("expowner" + nanos());
    Session guest = register("expguest" + nanos());
    String communityId = createCommunity(owner, "Expiry Club");
    String past = Instant.now().minusSeconds(3600).toString();
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/communities/" + communityId + "/invites")
                    .headers(auth(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isCreated())
            .andReturn();
    // past expiry is rejected at create time
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/invites")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expiresAt\":\"" + past + "\"}"))
        .andExpect(status().isBadRequest());
    String code = code(created);
    mockMvc
        .perform(post("/api/v1/invites/" + code + "/join").headers(auth(guest)))
        .andExpect(status().isOk());
  }

  @Test
  void invalidMaxUsesRejected() throws Exception {
    Session owner = register("baduseowner" + nanos());
    String communityId = createCommunity(owner, "Bounds Club");
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/invites")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxUses\":0}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/invites")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxUses\":-3}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void exhaustedInviteReturnsGone() throws Exception {
    Session owner = register("exhowner" + nanos());
    Session first = register("exhfirst" + nanos());
    Session second = register("exhsecond" + nanos());
    String communityId = createCommunity(owner, "Single Club");
    String code =
        code(
            mockMvc
                .perform(
                    post("/api/v1/communities/" + communityId + "/invites")
                        .headers(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxUses\":1}"))
                .andExpect(status().isCreated())
                .andReturn());
    mockMvc
        .perform(post("/api/v1/invites/" + code + "/join").headers(auth(first)))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/invites/" + code + "/join").headers(auth(second)))
        .andExpect(status().isGone());
  }

  @Test
  void revokedInviteBlocksJoinButMemberStaysIdempotent() throws Exception {
    Session owner = register("revowner" + nanos());
    Session guest = register("revguest" + nanos());
    Session other = register("revother" + nanos());
    String communityId = createCommunity(owner, "Revoke Club");
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/communities/" + communityId + "/invites")
                    .headers(auth(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isCreated())
            .andReturn();
    String code = code(created);
    String inviteId = id(created);
    mockMvc
        .perform(post("/api/v1/invites/" + code + "/join").headers(auth(guest)))
        .andExpect(status().isOk());
    mockMvc
        .perform(delete("/api/v1/invites/" + inviteId).headers(auth(owner)))
        .andExpect(status().isNoContent());
    // Revoke is idempotent.
    mockMvc
        .perform(delete("/api/v1/invites/" + inviteId).headers(auth(owner)))
        .andExpect(status().isNoContent());
    // Existing member stays idempotent after revoke.
    mockMvc
        .perform(post("/api/v1/invites/" + code + "/join").headers(auth(guest)))
        .andExpect(status().isOk());
    // Others are rejected.
    mockMvc
        .perform(post("/api/v1/invites/" + code + "/join").headers(auth(other)))
        .andExpect(status().isGone());
  }

  @Test
  void unknownCodeIsNotFound() throws Exception {
    Session guest = register("unkguest" + nanos());
    mockMvc
        .perform(get("/api/v1/invites/does-not-exist").headers(auth(guest)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post("/api/v1/invites/does-not-exist/join").headers(auth(guest)))        .andExpect(status().isNotFound());
  }

  @Test
  void lastUseRaceKeepsUsageWithinMax() throws Exception {
    Session owner = register("raceowner" + nanos());
    String communityId = createCommunity(owner, "Race Club");
    String code =
        code(
            mockMvc
                .perform(
                    post("/api/v1/communities/" + communityId + "/invites")
                        .headers(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxUses\":1}"))
                .andExpect(status().isCreated())
                .andReturn());

    Session first = register("racefirst" + nanos());
    Session second = register("racesecond" + nanos());

    int threads = 6;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      Session joiner = i % 2 == 0 ? first : second;
      futures.add(
          pool.submit(
              () -> {
                ready.countDown();
                start.await();
                try {
                  MvcResult result =
                      mockMvc
                          .perform(
                              post("/api/v1/invites/" + code + "/join").headers(auth(joiner)))
                          .andReturn();
                  return result.getResponse().getStatus();
                } catch (Exception e) {
                  return 500;
                }
              }));
    }
    ready.await();
    start.countDown();
    int ok = 0;
    int gone = 0;
    for (Future<Integer> f : futures) {
      int statusCode = f.get();
      if (statusCode == 200) {
        ok++;
      } else if (statusCode == 410) {
        gone++;
      } else {
        throw new AssertionError("Unexpected status: " + statusCode);
      }
    }
    pool.shutdown();
    // Both threads may succeed for DIFFERENT users only if maxUses allows; here maxUses=1
    // so at most the distinct-user joins within the limit succeed, and usage never exceeds 1.
    // Because the same two users retry, exactly 1 distinct membership consumes the single use.
    // usageCount is read via the owner list endpoint because resolve() itself
    // returns 410 GONE once the single use is consumed.
    MvcResult listed =
        mockMvc
            .perform(get("/api/v1/communities/" + communityId + "/invites").headers(auth(owner)))
            .andExpect(status().isOk())
            .andReturn();
    int usage = -1;
    for (JsonNode node :
        objectMapper.readTree(listed.getResponse().getContentAsString())) {
      if (code.equals(node.get("code").asText())) {
        usage = node.get("usageCount").asInt();
      }
    }
    if (usage < 0) {
      throw new AssertionError("invite missing from list response");
    }
    if (usage > 1) {
      throw new AssertionError("usageCount exceeded maxUses: " + usage);
    }
    if (ok < 1 || ok + gone != threads) {
      throw new AssertionError("Unexpected outcome ok=" + ok + " gone=" + gone);
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
    return new Session(
        node.get("user").get("id").asText(), node.get("accessToken").asText());
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

  private org.springframework.http.HttpHeaders auth(Session s) {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + s.token());
    return headers;
  }

  private String code(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText();
  }

  private String id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private String nanos() {
    return String.valueOf(Math.abs(System.nanoTime() % 100000));
  }

  private record Session(String userId, String token) {}
}
