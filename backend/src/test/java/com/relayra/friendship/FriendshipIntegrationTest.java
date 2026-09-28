package com.relayra.friendship;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FriendshipIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void fullFriendshipLifecycle() throws Exception {
    Session alice = register("friendalice" + nanos());
    Session bob = register("friendbob" + nanos());

    MvcResult send =
        mockMvc
            .perform(
                post("/api/v1/friends/requests")
                    .headers(authHeader(alice))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"receiverId\":\"" + bob.userId() + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn();
    String requestId = id(send);

    mockMvc
        .perform(
            post("/api/v1/friends/requests")
                .headers(authHeader(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"" + alice.userId() + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));

    mockMvc
        .perform(get("/api/v1/friends/requests/incoming").headers(authHeader(bob)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(requestId));

    mockMvc
        .perform(
            post("/api/v1/friends/requests/" + requestId + "/accept").headers(authHeader(bob)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"));

    mockMvc
        .perform(get("/api/v1/friends").headers(authHeader(alice)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].userId").value(bob.userId()));

    mockMvc
        .perform(delete("/api/v1/friends/" + bob.userId()).headers(authHeader(alice)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/friends").headers(authHeader(alice)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void senderCanCancelReceiverCanReject() throws Exception {
    Session alice = register("cancelalice" + nanos());
    Session bob = register("cancelbob" + nanos());
    Session carol = register("rejectcarol" + nanos());
    Session dave = register("rejectdave" + nanos());

    String cancelId =
        id(
            mockMvc
                .perform(
                    post("/api/v1/friends/requests")
                        .headers(authHeader(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":\"" + bob.userId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    mockMvc
        .perform(delete("/api/v1/friends/requests/" + cancelId).headers(authHeader(alice)))
        .andExpect(status().isNoContent());

    String rejectId =
        id(
            mockMvc
                .perform(
                    post("/api/v1/friends/requests")
                        .headers(authHeader(carol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":\"" + dave.userId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    mockMvc
        .perform(
            post("/api/v1/friends/requests/" + rejectId + "/reject").headers(authHeader(dave)))
        .andExpect(status().isNoContent());
  }

  @Test
  void onlyReceiverCanAccept() throws Exception {
    Session alice = register("acceptalice" + nanos());
    Session bob = register("acceptbob" + nanos());
    Session eve = register("accepteve" + nanos());

    String requestId =
        id(
            mockMvc
                .perform(
                    post("/api/v1/friends/requests")
                        .headers(authHeader(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":\"" + bob.userId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    mockMvc
        .perform(
            post("/api/v1/friends/requests/" + requestId + "/accept").headers(authHeader(alice)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/friends/requests/" + requestId + "/accept").headers(authHeader(eve)))
        .andExpect(status().isForbidden());
  }

  @Test
  void selfRequestIsRejected() throws Exception {
    Session alice = register("selfreq" + nanos());
    mockMvc
        .perform(
            post("/api/v1/friends/requests")
                .headers(authHeader(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"" + alice.userId() + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void blockPreventsRequestsAndEndsFriendship() throws Exception {
    Session alice = register("blockalice" + nanos());
    Session bob = register("blockbob" + nanos());

    String requestId =
        id(
            mockMvc
                .perform(
                    post("/api/v1/friends/requests")
                        .headers(authHeader(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":\"" + bob.userId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    mockMvc
        .perform(
            post("/api/v1/friends/requests/" + requestId + "/accept").headers(authHeader(bob)))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/api/v1/users/" + bob.userId() + "/block").headers(authHeader(alice)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/friends").headers(authHeader(alice)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    mockMvc
        .perform(
            post("/api/v1/friends/requests")
                .headers(authHeader(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"" + bob.userId() + "\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_BLOCKED"));

    mockMvc
        .perform(
            post("/api/v1/friends/requests")
                .headers(authHeader(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"" + alice.userId() + "\"}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/v1/friends/blocked").headers(authHeader(alice)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].userId").value(bob.userId()));

    mockMvc
        .perform(delete("/api/v1/users/" + bob.userId() + "/block").headers(authHeader(alice)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/friends/requests")
                .headers(authHeader(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"" + bob.userId() + "\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void repeatRejectCycleNeverServerErrors() throws Exception {
    Session alice = register("cyclealice" + nanos());
    Session bob = register("cyclebob" + nanos());

    for (int i = 0; i < 2; i++) {
      String requestId =
          id(
              mockMvc
                  .perform(
                      post("/api/v1/friends/requests")
                          .headers(authHeader(alice))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"receiverId\":\"" + bob.userId() + "\"}"))
                  .andExpect(status().isCreated())
                  .andReturn());
      mockMvc
          .perform(
              post("/api/v1/friends/requests/" + requestId + "/reject").headers(authHeader(bob)))
          .andExpect(status().isNoContent());
    }
  }

  @Test
  void concurrentDuplicateRequestsStaySingle() throws Exception {
    Session alice = register("racealice" + nanos());
    Session bob = register("racebob" + nanos());

    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      boolean aliceSends = i % 2 == 0;
      futures.add(
          pool.submit(
              () -> {
                ready.countDown();
                start.await();
                Session sender = aliceSends ? alice : bob;
                String target = aliceSends ? bob.userId() : alice.userId();
                try {
                  MockHttpServletRequestBuilder req =
                      post("/api/v1/friends/requests")
                          .headers(authHeader(sender))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"receiverId\":\"" + target + "\"}");
                  MvcResult result = mockMvc.perform(req).andReturn();
                  return result.getResponse().getStatus();
                } catch (Exception e) {
                  return 500;
                }
              }));
    }
    ready.await();
    start.countDown();
    int created = 0;
    int conflicts = 0;
    for (Future<Integer> f : futures) {
      int code = f.get();
      if (code == 201) {
        created++;
      } else if (code == 409) {
        conflicts++;
      } else {
        throw new AssertionError("Unexpected status: " + code);
      }
    }
    pool.shutdown();
    if (created != 1 || conflicts != threads - 1) {
      throw new AssertionError(
          "Expected exactly 1 created and "
              + (threads - 1)
              + " conflicts, got "
              + created
              + " created.");
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

  private org.springframework.http.HttpHeaders authHeader(Session s) {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + s.token());
    return headers;
  }

  private String id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private String nanos() {
    return String.valueOf(Math.abs(System.nanoTime() % 100000));
  }

  private record Session(String userId, String token) {}
}
