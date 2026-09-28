package com.relayra.role;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AuthorizationIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void ownerHasAllPermissionsAndCommunityGetsEveryoneRole() throws Exception {
    Session owner = register("roleowner" + nanos());
    String communityId = createCommunity(owner, "Role Defaults");

    mockMvc
        .perform(get("/api/v1/communities/" + communityId + "/permissions/me").headers(auth(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions.length()").value(14))
        .andExpect(jsonPath("$.permissions[?(@ == 'MANAGE_ROLES')]").exists());
    mockMvc
        .perform(get("/api/v1/communities/" + communityId + "/roles").headers(auth(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("@everyone"))
        .andExpect(jsonPath("$[0].managed").value(true))
        .andExpect(jsonPath("$[0].permissions.length()").value(4));
  }

  @Test
  void roleCrudAssignmentAndDelegatedChannelPermission() throws Exception {
    Session owner = register("crudroleowner" + nanos());
    Session manager = register("crudrolemanager" + nanos());
    String communityId = createCommunity(owner, "Role CRUD");
    join(owner, manager, communityId);

    String roleId = createRole(owner, communityId, "Channel Manager", "MANAGE_CHANNELS");
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/members/" + manager.userId() + "/roles")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleId\":\"" + roleId + "\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/channels")
                .headers(auth(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"delegated\",\"type\":\"TEXT\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            patch("/api/v1/roles/" + roleId)
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Channel Lead\",\"color\":\"#3366ff\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Channel Lead"))
        .andExpect(jsonPath("$.color").value("#3366FF"));

    mockMvc
        .perform(
            delete(
                    "/api/v1/communities/"
                        + communityId
                        + "/members/"
                        + manager.userId()
                        + "/roles/"
                        + roleId)
                .headers(auth(owner)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/channels")
                .headers(auth(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"blocked\",\"type\":\"TEXT\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/v1/roles/" + roleId).headers(auth(owner)))
        .andExpect(status().isNoContent());
  }

  @Test
  void managedRoleCannotBeMutatedAssignedOrDeleted() throws Exception {
    Session owner = register("managedowner" + nanos());
    Session member = register("managedmember" + nanos());
    String communityId = createCommunity(owner, "Managed Roles");
    join(owner, member, communityId);
    String everyoneId = firstRoleId(owner, communityId);

    mockMvc
        .perform(
            patch("/api/v1/roles/" + everyoneId)
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"hacked\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/v1/roles/" + everyoneId).headers(auth(owner)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/members/" + member.userId() + "/roles")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleId\":\"" + everyoneId + "\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void hierarchyPreventsEscalationAndOwnerRemoval() throws Exception {
    Session owner = register("hierowner" + nanos());
    Session admin = register("hieradmin" + nanos());
    Session target = register("hiertarget" + nanos());
    String communityId = createCommunity(owner, "Hierarchy Club");
    join(owner, admin, communityId);
    join(owner, target, communityId);
    String adminRole = createRole(owner, communityId, "Admin", "MANAGE_ROLES");
    String peerRole = createRole(owner, communityId, "Peer Admin", "MANAGE_ROLES");
    assign(owner, communityId, admin.userId(), adminRole, 200);

    assign(admin, communityId, target.userId(), peerRole, 403);
    assign(admin, communityId, owner.userId(), adminRole, 403);
    mockMvc
        .perform(delete("/api/v1/roles/" + adminRole).headers(auth(admin)))
        .andExpect(status().isForbidden());
  }

  @Test
  void delegatedManagerCannotGrantMissingPermissionOrSelfAssign() throws Exception {
    Session owner = register("grantowner" + nanos());
    Session manager = register("grantmanager" + nanos());
    Session target = register("granttarget" + nanos());
    String communityId = createCommunity(owner, "Grant Safety");
    join(owner, manager, communityId);
    join(owner, target, communityId);
    String managerRole = createRole(owner, communityId, "Role Manager", "MANAGE_ROLES");
    assign(owner, communityId, manager.userId(), managerRole, 200);

    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/roles")
                .headers(auth(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Secret Admin\",\"permissions\":[\"MANAGE_COMMUNITY\"]}"))
        .andExpect(status().isForbidden());
    assign(manager, communityId, manager.userId(), managerRole, 403);
  }

  @Test
  void nonMemberCannotEnumerateRoles() throws Exception {
    Session owner = register("idorowner" + nanos());
    Session outsider = register("idoroutsider" + nanos());
    String communityId = createCommunity(owner, "Private Roles");
    mockMvc
        .perform(get("/api/v1/communities/" + communityId + "/roles").headers(auth(outsider)))
        .andExpect(status().isForbidden());
  }

  @Test
  void reorderRequiresExactCustomRoleSet() throws Exception {
    Session owner = register("reorderowner" + nanos());
    String communityId = createCommunity(owner, "Role Reorder");
    String first = createRole(owner, communityId, "First", "VIEW_AUDIT_LOG");
    String second = createRole(owner, communityId, "Second", "MANAGE_MEMBERS");

    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/roles/reorder")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleIdsJson(second, first)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(second))
        .andExpect(jsonPath("$[1].id").value(first));
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/roles/reorder")
                .headers(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleIdsJson(first)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void concurrentReordersKeepDensePositions() throws Exception {
    Session owner = register("rolerace" + nanos());
    String communityId = createCommunity(owner, "Role Race");
    String first = createRole(owner, communityId, "Race One", "VIEW_AUDIT_LOG");
    String second = createRole(owner, communityId, "Race Two", "MANAGE_MEMBERS");
    String third = createRole(owner, communityId, "Race Three", "BAN_MEMBERS");
    List<List<String>> orders =
        List.of(List.of(first, second, third), List.of(third, first, second));
    ExecutorService pool = Executors.newFixedThreadPool(6);
    CountDownLatch ready = new CountDownLatch(6);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> futures = new ArrayList<>();
    for (int index = 0; index < 6; index++) {
      List<String> order = orders.get(index % 2);
      futures.add(
          pool.submit(
              () -> {
                ready.countDown();
                start.await();
                return mockMvc
                    .perform(
                        post("/api/v1/communities/" + communityId + "/roles/reorder")
                            .headers(auth(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(roleIdsJson(order.toArray(String[]::new))))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              }));
    }
    ready.await();
    start.countDown();
    for (Future<Integer> future : futures) {
      if (future.get() != 200) {
        throw new AssertionError("Concurrent reorder failed.");
      }
    }
    pool.shutdown();
    JsonNode roles = listRoles(owner, communityId);
    if (roles.size() != 4
        || roles.get(0).get("position").asInt() != 0
        || roles.get(1).get("position").asInt() != 1
        || roles.get(2).get("position").asInt() != 2
        || roles.get(3).get("position").asInt() != 3) {
      throw new AssertionError("Concurrent reorder broke dense positions.");
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

  private void assign(
      Session actor, String communityId, String targetId, String roleId, int expectedStatus)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/communities/" + communityId + "/members/" + targetId + "/roles")
                .headers(auth(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleId\":\"" + roleId + "\"}"))
        .andExpect(status().is(expectedStatus));
  }

  private String firstRoleId(Session owner, String communityId) throws Exception {
    return listRoles(owner, communityId).get(0).get("id").asText();
  }

  private JsonNode listRoles(Session session, String communityId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/communities/" + communityId + "/roles").headers(auth(session)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private String roleIdsJson(String... roleIds) throws Exception {
    return objectMapper.writeValueAsString(java.util.Map.of("roleIds", List.of(roleIds)));
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
