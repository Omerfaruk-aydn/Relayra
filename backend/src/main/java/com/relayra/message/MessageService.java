package com.relayra.message;

import com.relayra.auth.RateLimitedException;
import com.relayra.auth.RateLimiter;
import com.relayra.auth.domain.Profile;
import com.relayra.auth.domain.User;
import com.relayra.auth.domain.UserStatus;
import com.relayra.auth.dto.UserSummary;
import com.relayra.auth.persistence.ProfileRepository;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.channel.domain.Channel;
import com.relayra.channel.persistence.ChannelRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.message.domain.Message;
import com.relayra.message.domain.MessageType;
import com.relayra.message.dto.EditMessageRequest;
import com.relayra.message.dto.MessagePageResponse;
import com.relayra.message.dto.MessageResponse;
import com.relayra.message.dto.SendMessageRequest;
import com.relayra.message.persistence.MessageRepository;
import com.relayra.realtime.MessageChangedEvent;
import com.relayra.role.PermissionService;
import com.relayra.role.domain.Permission;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageService {

  private final MessageRepository messages;
  private final ChannelRepository channels;
  private final UserRepository users;
  private final ProfileRepository profiles;
  private final PermissionService permissions;
  private final RateLimiter rateLimiter;
  private final ApplicationEventPublisher eventPublisher;

  public MessageService(
      MessageRepository messages,
      ChannelRepository channels,
      UserRepository users,
      ProfileRepository profiles,
      PermissionService permissions,
      RateLimiter rateLimiter,
      ApplicationEventPublisher eventPublisher) {
    this.messages = messages;
    this.channels = channels;
    this.users = users;
    this.profiles = profiles;
    this.permissions = permissions;
    this.rateLimiter = rateLimiter;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public MessageResponse sendToChannel(
      UUID callerId, UUID channelId, SendMessageRequest request) {
    User caller = requireActiveUserForUpdate(callerId);
    Channel channel = requireChannel(channelId);
    permissions.require(callerId, channel.getCommunityId(), Permission.SEND_MESSAGES);
    String clientMessageId = normalizeClientMessageId(request.clientMessageId());
    String content = normalizeContent(request.content());
    Message existing =
        messages.findByAuthorIdAndClientMessageId(callerId, clientMessageId).orElse(null);
    if (existing != null) {
      if (!channelId.equals(existing.getChannelId())) {
        throw new DomainException(
            HttpStatus.CONFLICT.value(),
            ErrorCodes.CONFLICT,
            "clientMessageId was already used in another scope.");
      }
      return toResponse(existing, caller, displayName(caller));
    }
    rateLimiter.check(
        RateLimitedException.deviceKey("message-send", callerId), 30, Duration.ofMinutes(1));
    if (request.replyToMessageId() != null) {
      Message reply = messages.findById(request.replyToMessageId()).orElse(null);
      if (reply == null
          || !channelId.equals(reply.getChannelId())
          || reply.getDeletedAt() != null) {
        throw new DomainException(
            HttpStatus.BAD_REQUEST.value(),
            ErrorCodes.VALIDATION_FAILED,
            "Reply target is unavailable in this channel.");
      }
    }
    Message message =
        new Message(
            UUID.randomUUID(),
            callerId,
            channelId,
            null,
            request.replyToMessageId(),
            content,
            MessageType.TEXT,
            clientMessageId);
    MessageResponse response =
        toResponse(messages.saveAndFlush(message), caller, displayName(caller));
    eventPublisher.publishEvent(new MessageChangedEvent("MESSAGE_CREATED", response));
    return response;
  }

  @Transactional(readOnly = true)
  public MessagePageResponse history(
      UUID callerId,
      UUID channelId,
      Integer limit,
      Instant beforeCreatedAt,
      UUID beforeId) {
    requireActiveUser(callerId);
    Channel channel = requireChannel(channelId);
    permissions.require(callerId, channel.getCommunityId(), Permission.VIEW_CHANNEL);
    int size = limit == null ? 50 : limit;
    if (size < 1 || size > 100) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "limit must be between 1 and 100.");
    }
    if ((beforeCreatedAt == null) != (beforeId == null)) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "beforeCreatedAt and beforeId must be provided together.");
    }
    List<Message> page =
        messages.findChannelHistory(
            channelId, beforeCreatedAt, beforeId, PageRequest.of(0, size));
    Map<UUID, User> authors =
        users.findAllById(page.stream().map(Message::getAuthorId).distinct().toList()).stream()
            .collect(Collectors.toMap(User::getId, user -> user));
    Map<UUID, String> displayNames =
        profiles.findByUserIdIn(List.copyOf(authors.keySet())).stream()
            .collect(Collectors.toMap(Profile::getUserId, Profile::getDisplayName));
    List<MessageResponse> responses =
        page.stream()
            .map(
                message -> {
                  User author = authors.get(message.getAuthorId());
                  if (author == null) {
                    author = requireUser(message.getAuthorId());
                  }
                  return toResponse(
                      message,
                      author,
                      displayNames.getOrDefault(message.getAuthorId(), author.getUsername()));
                })
            .toList();
    Message last = page.isEmpty() ? null : page.get(page.size() - 1);
    return new MessagePageResponse(
        responses,
        last == null ? null : last.getCreatedAt(),
        last == null ? null : last.getId());
  }

  @Transactional
  public MessageResponse edit(UUID callerId, UUID messageId, EditMessageRequest request) {
    requireActiveUser(callerId);
    Message snapshot = requireMessage(messageId);
    Channel channel = requireMessageChannel(snapshot);
    permissions.require(callerId, channel.getCommunityId(), Permission.VIEW_CHANNEL);
    if (!snapshot.getAuthorId().equals(callerId)) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.INSUFFICIENT_PERMISSION,
          "Only the author can edit this message.");
    }
    Message message = requireMessageForUpdate(messageId);
    if (!message.getAuthorId().equals(callerId)
        || !channel.getId().equals(message.getChannelId())) {
      throw new DomainException(
          HttpStatus.NOT_FOUND.value(), ErrorCodes.RESOURCE_NOT_FOUND, "Message was not found.");
    }
    if (message.getDeletedAt() != null) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(), ErrorCodes.MESSAGE_DELETED, "Message was deleted.");
    }
    if (message.getType() != MessageType.TEXT) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Only text messages can be edited.");
    }
    message.edit(normalizeContent(request.content()));
    User author = requireUser(message.getAuthorId());
    MessageResponse response =
        toResponse(messages.saveAndFlush(message), author, displayName(author));
    eventPublisher.publishEvent(new MessageChangedEvent("MESSAGE_UPDATED", response));
    return response;
  }

  @Transactional
  public void delete(UUID callerId, UUID messageId) {
    requireActiveUser(callerId);
    Message snapshot = requireMessage(messageId);
    Channel channel = requireMessageChannel(snapshot);
    if (!snapshot.getAuthorId().equals(callerId)) {
      permissions.require(callerId, channel.getCommunityId(), Permission.DELETE_MESSAGES);
    } else {
      permissions.require(callerId, channel.getCommunityId(), Permission.VIEW_CHANNEL);
    }
    Message message = requireMessageForUpdate(messageId);
    if (!channel.getId().equals(message.getChannelId())) {
      throw new DomainException(
          HttpStatus.NOT_FOUND.value(), ErrorCodes.RESOURCE_NOT_FOUND, "Message was not found.");
    }
    message.softDelete();
    messages.flush();
    User author = requireUser(message.getAuthorId());
    eventPublisher.publishEvent(
        new MessageChangedEvent(
            "MESSAGE_DELETED", toResponse(message, author, displayName(author))));
  }

  private User requireActiveUser(UUID userId) {
    User user = requireUser(userId);
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(), ErrorCodes.ACCESS_DENIED, "Account is disabled.");
    }
    return user;
  }

  private User requireActiveUserForUpdate(UUID userId) {
    User user =
        users
            .findByIdForUpdate(userId)
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

  private User requireUser(UUID userId) {
    return users
        .findById(userId)
        .orElseThrow(
            () ->
                new DomainException(
                    HttpStatus.NOT_FOUND.value(),
                    ErrorCodes.RESOURCE_NOT_FOUND,
                    "User was not found."));
  }

  private Channel requireChannel(UUID channelId) {
    return channels
        .findById(channelId)
        .orElseThrow(
            () ->
                new DomainException(
                    HttpStatus.NOT_FOUND.value(),
                    ErrorCodes.RESOURCE_NOT_FOUND,
                    "Channel was not found."));
  }

  private Message requireMessage(UUID messageId) {
    return messages.findById(messageId).orElseThrow(this::messageNotFound);
  }

  private Message requireMessageForUpdate(UUID messageId) {
    return messages.findByIdForUpdate(messageId).orElseThrow(this::messageNotFound);
  }

  private Channel requireMessageChannel(Message message) {
    if (message.getChannelId() == null) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Conversation messages are not supported yet.");
    }
    return requireChannel(message.getChannelId());
  }

  private String normalizeContent(String value) {
    String content = value == null ? "" : value;
    boolean hasVisibleContent =
        content.codePoints()
            .anyMatch(
                codePoint ->
                    !Character.isWhitespace(codePoint)
                        && !Character.isSpaceChar(codePoint)
                        && !Character.isISOControl(codePoint)
                        && Character.getType(codePoint) != Character.FORMAT);
    if (!hasVisibleContent || content.length() > 4000) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Message content must be between 1 and 4000 characters.");
    }
    return content.strip();
  }

  private String normalizeClientMessageId(String value) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty() || normalized.length() > 128) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "clientMessageId must be between 1 and 128 characters.");
    }
    return normalized;
  }

  private String displayName(User user) {
    return profiles.findByUserId(user.getId()).map(Profile::getDisplayName).orElse(user.getUsername());
  }

  private DomainException messageNotFound() {
    return new DomainException(
        HttpStatus.NOT_FOUND.value(), ErrorCodes.RESOURCE_NOT_FOUND, "Message was not found.");
  }

  private MessageResponse toResponse(Message message, User author, String displayName) {
    return new MessageResponse(
        message.getId(),
        message.getClientMessageId(),
        message.getChannelId(),
        message.getConversationId(),
        new UserSummary(author.getId(), author.getUsername(), displayName),
        message.getContent(),
        message.getType(),
        message.getReplyToMessageId(),
        message.getCreatedAt(),
        message.getEditedAt(),
        message.getDeletedAt());
  }
}
