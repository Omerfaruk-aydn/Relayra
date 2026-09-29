package com.relayra.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.relayra.auth.domain.User;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.notification.persistence.NotificationRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class MentionParserTest {

  private UserRepository users;
  private NotificationService service;

  @BeforeEach
  void setUp() {
    NotificationRepository notifications = mock(NotificationRepository.class);
    users = mock(UserRepository.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    service = new NotificationService(notifications, users, eventPublisher);
  }

  @Test
  void nullAndEmptyContentReturnNoMentions() {
    UUID authorId = UUID.randomUUID();

    assertTrue(service.parseMentions(null, authorId).isEmpty());
    assertTrue(service.parseMentions("", authorId).isEmpty());
  }

  @Test
  void unknownUsernameIsIgnored() {
    when(users.findByUsernameNormalized("unknown")).thenReturn(Optional.empty());

    assertTrue(service.parseMentions("hello @unknown", UUID.randomUUID()).isEmpty());
  }

  @Test
  void selfMentionIsExcluded() {
    UUID authorId = UUID.randomUUID();
    when(users.findByUsernameNormalized("author"))
        .thenReturn(Optional.of(activeUser(authorId, "author")));

    assertTrue(service.parseMentions("hello @author", authorId).isEmpty());
  }

  @Test
  void duplicateNamesAreDeduplicatedCaseInsensitively() {
    UUID mentionedId = UUID.randomUUID();
    when(users.findByUsernameNormalized("alice"))
        .thenReturn(Optional.of(activeUser(mentionedId, "alice")));

    assertEquals(
        List.of(mentionedId),
        service.parseMentions("@Alice and again @alice and @ALICE", UUID.randomUUID()));
  }

  @Test
  void mentionsAreCappedAtTwentyDistinctUsers() {
    List<UUID> expected = new ArrayList<>();
    StringBuilder content = new StringBuilder();
    for (int index = 0; index < 25; index++) {
      String username = "user%02d".formatted(index);
      UUID userId = UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8));
      expected.add(userId);
      content.append('@').append(username).append(' ');
      when(users.findByUsernameNormalized(username))
          .thenReturn(Optional.of(activeUser(userId, username)));
    }

    assertEquals(expected.subList(0, 20), service.parseMentions(content.toString(), UUID.randomUUID()));
  }

  @Test
  void inactiveUserIsExcluded() {
    User inactiveUser = activeUser(UUID.randomUUID(), "disabled");
    inactiveUser.disable();
    when(users.findByUsernameNormalized("disabled")).thenReturn(Optional.of(inactiveUser));

    assertTrue(service.parseMentions("hello @disabled", UUID.randomUUID()).isEmpty());
  }

  private User activeUser(UUID id, String username) {
    return new User(
        id,
        username,
        username.toLowerCase(),
        username + "@example.com",
        username.toLowerCase() + "@example.com",
        "password-hash");
  }
}
