package com.relayra.notification;

import com.relayra.auth.domain.User;
import com.relayra.auth.domain.UserStatus;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.notification.domain.Notification;
import com.relayra.notification.domain.NotificationType;
import com.relayra.notification.dto.NotificationEventData;
import com.relayra.notification.dto.NotificationPageResponse;
import com.relayra.notification.dto.NotificationResponse;
import com.relayra.notification.persistence.NotificationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
  private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_]{3,32})");

  private final NotificationRepository notifications;
  private final UserRepository users;
  private final ApplicationEventPublisher eventPublisher;

  public NotificationService(
      NotificationRepository notifications,
      UserRepository users,
      ApplicationEventPublisher eventPublisher) {
    this.notifications = notifications;
    this.users = users;
    this.eventPublisher = eventPublisher;
  }

  @Transactional(readOnly = true)
  public NotificationPageResponse list(
      UUID callerId, Integer limit, Instant beforeCreatedAt, UUID beforeId) {
    requireActive(callerId);
    validateCursor(beforeCreatedAt, beforeId);
    List<Notification> page =
        notifications.findUserHistory(callerId, beforeCreatedAt, beforeId, pageRequest(limit));
    long unreadCount = notifications.countByUserIdAndReadAtIsNull(callerId);
    Notification last = page.isEmpty() ? null : page.get(page.size() - 1);
    return new NotificationPageResponse(
        page.stream().map(this::toResponse).toList(),
        unreadCount,
        last == null ? null : last.getCreatedAt(),
        last == null ? null : last.getId());
  }

  @Transactional
  public NotificationResponse markRead(UUID callerId, UUID notificationId) {
    requireActive(callerId);
    Notification notification =
        notifications
            .findByIdForUpdate(notificationId)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "Notification was not found."));
    if (!notification.getUserId().equals(callerId)) {
      throw new DomainException(
          HttpStatus.NOT_FOUND.value(),
          ErrorCodes.RESOURCE_NOT_FOUND,
          "Notification was not found.");
    }
    notification.markRead();
    return toResponse(notifications.saveAndFlush(notification));
  }

  @Transactional
  public void markAllRead(UUID callerId) {
    requireActive(callerId);
    List<Notification> unread = notifications.findByUserIdAndReadAtIsNull(callerId);
    for (Notification notification : unread) {
      notification.markRead();
    }
    notifications.saveAll(unread);
    notifications.flush();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void notify(
      UUID userId,
      NotificationType type,
      UUID actorId,
      UUID communityId,
      UUID channelId,
      UUID conversationId,
      UUID messageId,
      UUID referenceId,
      String detail) {
    if (userId == null || userId.equals(actorId)) {
      return;
    }
    if (messageId != null
        && notifications
            .findByUserIdAndTypeAndActorIdAndMessageId(userId, type, actorId, messageId)
            .isPresent()) {
      return;
    }
    try {
      Notification notification = new Notification(UUID.randomUUID(), userId, type);
      notification.link(actorId, communityId, channelId, conversationId, messageId, referenceId, detail);
      Notification saved = notifications.saveAndFlush(notification);
      publish(saved);
    } catch (DataIntegrityViolationException duplicate) {
      log.debug("Duplicate notification suppressed for user {} type {}", userId, type);
    } catch (RuntimeException failure) {
      log.warn("Notification delivery failed for user {} type {}: {}", userId, type, failure.toString());
    }
  }

  public void pushAfterCommit(com.relayra.realtime.NotificationChangedEvent event) {
    log.debug("Notification push bridged for type {}", event.type());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onFriendRequest(com.relayra.friendship.FriendRequestEvent event) {
    notify(
        event.receiverId(),
        NotificationType.FRIEND_REQUEST,
        event.senderId(),
        null,
        null,
        null,
        null,
        event.requestId(),
        null);
  }

  public List<UUID> parseMentions(String content, UUID authorId) {
    if (content == null || content.isEmpty()) {
      return List.of();
    }
    Matcher matcher = MENTION_PATTERN.matcher(content);
    List<String> names = new ArrayList<>();
    while (matcher.find()) {
      String name = matcher.group(1);
      boolean known = false;
      for (String existing : names) {
        if (existing.equalsIgnoreCase(name)) {
          known = true;
          break;
        }
      }
      if (!known) {
        names.add(name);
      }
      if (names.size() >= 20) {
        break;
      }
    }
    if (names.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = new ArrayList<>();
    for (String name : names) {
      users
          .findByUsernameNormalized(name.toLowerCase(Locale.ROOT))
          .filter(user -> user.getStatus() == UserStatus.ACTIVE)
          .map(User::getId)
          .filter(id -> !id.equals(authorId) && !ids.contains(id))
          .ifPresent(ids::add);
    }
    return List.copyOf(ids);
  }

  private void publish(Notification notification) {
    long unreadCount = notifications.countByUserIdAndReadAtIsNull(notification.getUserId());
    NotificationEventData data =
        new NotificationEventData(
            notification.getId(),
            notification.getType(),
            notification.getActorId(),
            notification.getCommunityId(),
            notification.getChannelId(),
            notification.getConversationId(),
            notification.getMessageId(),
            notification.getReferenceId(),
            notification.getDetail(),
            unreadCount,
            notification.getCreatedAt());
    eventPublisher.publishEvent(
        new com.relayra.notification.NotificationChangedEventForPush(
            "NOTIFICATION_CREATED", notification.getUserId(), data));
  }

  private User requireActive(UUID userId) {
    User user =
        users
            .findById(userId)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "User was not found."));
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(), ErrorCodes.ACCESS_DENIED, "Account is disabled.");
    }
    return user;
  }

  private PageRequest pageRequest(Integer limit) {
    int size = limit == null ? 50 : limit;
    if (size < 1 || size > 100) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "limit must be between 1 and 100.");
    }
    return PageRequest.of(0, size);
  }

  private void validateCursor(Instant beforeCreatedAt, UUID beforeId) {
    if ((beforeCreatedAt == null) != (beforeId == null)) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "beforeCreatedAt and beforeId must be provided together.");
    }
  }

  private NotificationResponse toResponse(Notification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getType(),
        notification.getActorId(),
        notification.getCommunityId(),
        notification.getChannelId(),
        notification.getConversationId(),
        notification.getMessageId(),
        notification.getReferenceId(),
        notification.getDetail(),
        notification.getReadAt(),
        notification.getCreatedAt());
  }
}
