package com.relayra.moderation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModerationIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void kickRemovesMemberAndRevokesAccess() throws Exception {
    Session owner = register("modkickowner" + nanos());
    Session member = register("modkickmember" + nanos());
    String communityId = createCommunity(owner, "Kick Club");
    join(owner, member, communityId);

    mockMvc
        .perform(delete("/api/v1/communities/" + communityId + "/members/" + member.userId())
            .headers(auth(owner)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/communities/" + communityId + "/channels").headers(auth(member)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/v1/communities/" + communityId + "/members/" + member.userId())
            .headers(auth(owner)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/communities/" + communityId + "/channels").headers(auth(owner)))
        .andExpect(status().isOk());

    MvcResult audit =
        mockMvc
            .perform(
                get("/api/v1/communities/" + communityId + "/audit-log").headers(auth(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[?(@.action == 'KICK')]").exists())
            .andReturn();
    JsonNode kick =
        objectMapper
            .readTree(audit.getResponse().getContentAsString())
            .get("entries")
            .elements()
            .next();
    if (kick == null) {
      throw new AssertionError("Audit log should contain the kick entry.");
    }
  }

  @Test
  void ownerIsProtectedAndPeerCannotModerate() throws Exception {
    Session owner = register("modhierowner" + nanos());
    Session admin = register("modhieradmin" + nanos());
    Session peer = register("modhierpeer" + nanos());
    String communityId = createCommunity(owner, "Hierarchy Moderation");
    join(owner, admin, communityId);
    join(owner, peer, communityId);
    String adminRole = createRole(owner, communityId, "Mod Admin", "KICK_MEMBERS");
    String peerRole = createRole(owner, communityId, "Mod Peer", "KICK_MEMBERS");
    assign(owner, communityId, admin.userId(), adminRole);
    assign(owner, communityId, peer.userId(), peerRole);

    mockMvc
        .perform(delete("/api/v1/communities/" + communityId + "/members/" + owner.userId())
            .headers(auth(admin)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/v1/communities/" + communityId + "/members/" + peer.userId())
            .headers(auth(admin)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/v1/communities/" + communityId + "/members/" + admin.userId())
            .headers(auth(owner)))
        .andExpect(status().isNoContent());
  }

  @Test
  void banBlocksJoinMessagingAndSubscriptionThenUnbanRestoresJoin() throws Exception {
    Session owner = register("modbanowner" + nanos());
    Session target = register("modbantarget" + nanos());
    String communityId = createCommunity(owner, "Ban Hall");
    join(owner, target, communityId);

    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/bans")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + target.userId() + "\",\"reason\":\"spam\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value(target.userId()));

    mockMvc
        .perform(get("/api/v1/communities/" + communityId + "/channels").headers(auth(target)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_BANNED"));
    String channelId = firstChannelId(owner, communityId);
    mockMvc
        .perform(
            post("/api/v1/channels/" + channelId + "/messages")
                .headers(auth(target))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientMessageId\":\"banned-1\",\"content\":\"hi\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_BANNED"));

    MvcResult inviteResult =
        mockMvc
            .perform(
                post("/api/v1/communities/" + communityId + "/invites")
                    .headers(auth(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isCreated())
            .andReturn();
    String code =
        objectMapper.readTree(inviteResult.getResponse().getContentAsString()).get("code").asText();
    mockMvc
        .perform(post("/api/v1/invites/" + code + "/join").headers(auth(target)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_BANNED"));

    mockMvc
        .perform(
            delete("/api/v1/communities/" + communityId + "/bans/" + target.userId())
                .headers(auth(owner)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/communities/" + communityId + "/channels").headers(auth(target)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/v1/invites/" + code + "/join").headers(auth(target)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/communities/" + communityId + "/audit-log").headers(auth(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries[?(@.action == 'BAN')]").exists())
        .andExpect(jsonPath("$.entries[?(@.action == 'UNBAN')]").exists());
  }

  @Test
  void auditLogRequiresPermissionAndMemberCannotRead() throws Exception {
    Session owner = register("modauditowner" + nanos());
    Session member = register("modauditmember" + nanos());
    String communityId = createCommunity(owner, "Audit Gate");
    join(owner, member, communityId);

    mockMvc
        .perform(get("/api/v1/communities/" + communityId + "/audit-log").headers(auth(member)))
        .andExpect(status().isForbidden());
    Session outsider = register("modauditout" + nanos());
    mockMvc
        .perform(get("/api/v1/communities/" + communityId + "/audit-log").headers(auth(outsider)))
        .andExpect(status().isForbidden());
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

  private void assign(Session actor, String communityId, String targetId, String roleId)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/members/" + targetId + "/roles")
                .headers(auth(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleId\":\"" + roleId + "\"}"))
        .andExpect(status().isOk());
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
