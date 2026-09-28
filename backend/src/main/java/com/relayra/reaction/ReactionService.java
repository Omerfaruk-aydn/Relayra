package com.relayra.reaction;

import com.relayra.auth.RateLimitedException;
import com.relayra.auth.RateLimiter;
import com.relayra.auth.domain.Profile;
import com.relayra.auth.domain.User;
import com.relayra.auth.domain.UserStatus;
import com.relayra.auth.dto.UserSummary;
import com.relayra.auth.persistence.ProfileRepository;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.channel.persistence.ChannelRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.conversation.ConversationService;
import com.relayra.message.domain.Message;
import com.relayra.message.persistence.MessageRepository;
import com.relayra.reaction.domain.MessageReaction;
import com.relayra.reaction.dto.ReactionEventData;
import com.relayra.reaction.dto.ReactionResponse;
import com.relayra.reaction.persistence.MessageReactionRepository;
import com.relayra.realtime.ReactionChangedEvent;
import com.relayra.role.PermissionService;
import com.relayra.role.domain.Permission;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReactionService {

  private final MessageReactionRepository reactions;
  private final MessageRepository messages;
  private final ChannelRepository channels;
  private final ConversationService conversations;
  private final UserRepository users;
  private final ProfileRepository profiles;
  private final PermissionService permissions;
  private final RateLimiter rateLimiter;
  private final ApplicationEventPublisher eventPublisher;

  public ReactionService(
      MessageReactionRepository reactions,
      MessageRepository messages,
      ChannelRepository channels,
      ConversationService conversations,
      UserRepository users,
      ProfileRepository profiles,
      PermissionService permissions,
      RateLimiter rateLimiter,
      ApplicationEventPublisher eventPublisher) {
    this.reactions = reactions;
    this.messages = messages;
    this.channels = channels;
    this.conversations = conversations;
    this.users = users;
    this.profiles = profiles;
    this.permissions = permissions;
    this.rateLimiter = rateLimiter;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public List<ReactionResponse> add(UUID callerId, UUID messageId, String emoji) {
    User caller = requireActive(callerId);
    String normalized = normalizeEmoji(emoji);
    Message message = requireMessageForUpdate(messageId);
    requireMutationAllowed(callerId, message);
    if (message.getDeletedAt() != null) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.MESSAGE_DELETED,
          "Deleted messages cannot receive reactions.");
    }
    if (reactions
        .findByMessageIdAndUserIdAndEmoji(messageId, callerId, normalized)
        .isPresent()) {
      return summarize(messageId, callerId);
    }
    rateLimiter.check(
        RateLimitedException.deviceKey("reaction-add", callerId), 60, Duration.ofMinutes(1));
    try {
      reactions.saveAndFlush(new MessageReaction(UUID.randomUUID(), messageId, callerId, normalized));
    } catch (RuntimeException duplicate) {
      if (!isDuplicateKey(duplicate)) {
        throw duplicate;
      }
      return summarize(messageId, callerId);
    }
    publish(message, caller, normalized, "REACTION_ADDED");
    return summarize(messageId, callerId);
  }

  @Transactional
  public void remove(UUID callerId, UUID messageId, String emoji) {
    requireActive(callerId);
    String normalized = normalizeEmoji(emoji);
    Message message = requireMessageForUpdate(messageId);
    requireMutationAllowed(callerId, message);
    if (reactions
        .findByMessageIdAndUserIdAndEmoji(messageId, callerId, normalized)
        .isEmpty()) {
      return;
    }
    rateLimiter.check(
        RateLimitedException.deviceKey("reaction-remove", callerId), 60, Duration.ofMinutes(1));
    long deleted = reactions.deleteByMessageIdAndUserIdAndEmoji(messageId, callerId, normalized);
    reactions.flush();
    if (deleted == 0) {
      return;
    }
    publish(message, requireUser(callerId), normalized, "REACTION_REMOVED");
  }

  @Transactional(readOnly = true)
  public List<ReactionResponse> list(UUID callerId, UUID messageId) {
    requireActive(callerId);
    Message message = requireMessage(messageId);
    requireReadAllowed(callerId, message);
    return summarize(messageId, callerId);
  }

  private void requireScopeAccess(UUID callerId, Message message) {
    if (message.getChannelId() != null) {
      var channel =
          channels
              .findById(message.getChannelId())
              .orElseThrow(
                  () ->
                      new DomainException(
                          HttpStatus.NOT_FOUND.value(),
                          ErrorCodes.RESOURCE_NOT_FOUND,
                          "Message was not found."));
      permissions.require(callerId, channel.getCommunityId(), Permission.VIEW_CHANNEL);
      return;
    }
    conversations.requireParticipant(callerId, message.getConversationId());
  }

  private void requireMutationAllowed(UUID callerId, Message message) {
    if (message.getChannelId() != null) {
      var channel =
          channels
              .findById(message.getChannelId())
              .orElseThrow(
                  () ->
                      new DomainException(
                          HttpStatus.NOT_FOUND.value(),
                          ErrorCodes.RESOURCE_NOT_FOUND,
                          "Message was not found."));
      permissions.require(callerId, channel.getCommunityId(), Permission.VIEW_CHANNEL);
      permissions.require(callerId, channel.getCommunityId(), Permission.ADD_REACTIONS);
      return;
    }
    conversations.requireMessagingAllowed(callerId, message.getConversationId());
  }

  private void requireReadAllowed(UUID callerId, Message message) {
    if (message.getChannelId() != null) {
      requireScopeAccess(callerId, message);
      return;
    }
    conversations.requireParticipant(callerId, message.getConversationId());
  }

  private void publish(Message message, User actor, String emoji, String type) {
    int count =
        (int)
            reactions.findByMessageId(message.getId()).stream()
                .filter(reaction -> reaction.getEmoji().equals(emoji))
                .count();
    eventPublisher.publishEvent(
        new ReactionChangedEvent(
            type,
            new ReactionEventData(
                message.getId(),
                message.getChannelId(),
                message.getConversationId(),
                emoji,
                new UserSummary(actor.getId(), actor.getUsername(), displayName(actor)),
                count,
                Instant.now())));
  }

  private List<ReactionResponse> summarize(UUID messageId, UUID viewerId) {
    Map<String, List<MessageReaction>> grouped =
        reactions.findByMessageId(messageId).stream()
            .collect(Collectors.groupingBy(MessageReaction::getEmoji));
    return grouped.entrySet().stream()
        .map(
            entry ->
                new ReactionResponse(
                    messageId,
                    entry.getKey(),
                    entry.getValue().size(),
                    entry.getValue().stream().anyMatch(reaction -> reaction.getUserId().equals(viewerId))))
        .sorted((first, second) -> first.emoji().compareTo(second.emoji()))
        .toList();
  }

  private String normalizeEmoji(String emoji) {
    String normalized =
        emoji == null ? "" : java.text.Normalizer.normalize(emoji.strip(), java.text.Normalizer.Form.NFC);
    if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 16) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Emoji must be between 1 and 16 characters.");
    }
    boolean hasVisibleContent =
        normalized.codePoints()
            .anyMatch(
                codePoint ->
                    !Character.isWhitespace(codePoint)
                        && !Character.isSpaceChar(codePoint)
                        && !Character.isISOControl(codePoint)
                        && Character.getType(codePoint) != Character.FORMAT);
    if (!hasVisibleContent) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Emoji must contain visible content.");
    }
    return normalized;
  }

  private User requireActive(UUID userId) {
    User user = requireUser(userId);
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

  private Message requireMessage(UUID messageId) {
    return messages
        .findById(messageId)
        .orElseThrow(
            () ->
                new DomainException(
                    HttpStatus.NOT_FOUND.value(),
                    ErrorCodes.RESOURCE_NOT_FOUND,
                    "Message was not found."));
  }

  private Message requireMessageForUpdate(UUID messageId) {
    return messages
        .findByIdForUpdate(messageId)
        .orElseThrow(
            () ->
                new DomainException(
                    HttpStatus.NOT_FOUND.value(),
                    ErrorCodes.RESOURCE_NOT_FOUND,
                    "Message was not found."));
  }

  private String displayName(User user) {
    return profiles.findByUserId(user.getId()).map(Profile::getDisplayName).orElse(user.getUsername());
  }

  private boolean isDuplicateKey(RuntimeException exception) {
    Throwable current = exception;
    while (current != null) {
      String message = current.getMessage() == null ? "" : current.getMessage();
      if (current instanceof DataIntegrityViolationException
          || message.contains("uq_message_reactions")
          || message.contains("Unique index or primary key violation")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
