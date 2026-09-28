package com.relayra.conversation;

import com.relayra.auth.domain.Profile;
import com.relayra.auth.domain.User;
import com.relayra.auth.domain.UserStatus;
import com.relayra.auth.persistence.ProfileRepository;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.conversation.domain.Conversation;
import com.relayra.conversation.domain.ConversationParticipant;
import com.relayra.conversation.dto.ConversationPeer;
import com.relayra.conversation.dto.ConversationResponse;
import com.relayra.conversation.persistence.ConversationParticipantRepository;
import com.relayra.conversation.persistence.ConversationRepository;
import com.relayra.friendship.persistence.UserBlockRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

  private final ConversationRepository conversations;
  private final ConversationParticipantRepository participants;
  private final UserRepository users;
  private final ProfileRepository profiles;
  private final UserBlockRepository blocks;

  public ConversationService(
      ConversationRepository conversations,
      ConversationParticipantRepository participants,
      UserRepository users,
      ProfileRepository profiles,
      UserBlockRepository blocks) {
    this.conversations = conversations;
    this.participants = participants;
    this.users = users;
    this.profiles = profiles;
    this.blocks = blocks;
  }

  @Transactional
  public ConversationResponse direct(UUID callerId, UUID peerId) {
    User caller = requireActive(callerId);
    User peer = requirePeer(peerId);
    if (caller.getId().equals(peer.getId())) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "You cannot start a conversation with yourself.");
    }
    lockUserPair(caller.getId(), peer.getId());
    if (blockedEitherWay(caller.getId(), peer.getId())) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.USER_BLOCKED,
          "Direct conversation is not allowed.");
    }
    String pairKey = Conversation.directPairKey(caller.getId(), peer.getId());
    Conversation existing = conversations.findByDirectPairKeyForUpdate(pairKey).orElse(null);
    if (existing != null) {
      return toResponse(existing);
    }
    Conversation created = conversations.saveAndFlush(new Conversation(UUID.randomUUID(), pairKey));
    participants.saveAllAndFlush(
        List.of(
            new ConversationParticipant(created.getId(), caller.getId()),
            new ConversationParticipant(created.getId(), peer.getId())));
    if (blockedEitherWay(caller.getId(), peer.getId())) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.USER_BLOCKED,
          "Direct conversation is not allowed.");
    }
    return toResponse(created);
  }

  @Transactional(readOnly = true)
  public List<ConversationResponse> list(UUID callerId) {
    requireActive(callerId);
    return participants.findByIdUserId(callerId).stream()
        .map(participant -> requireConversation(participant.getConversationId()))
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public ConversationResponse get(UUID callerId, UUID conversationId) {
    requireActive(callerId);
    Conversation conversation = requireConversation(conversationId);
    requireParticipant(callerId, conversationId);
    return toResponse(conversation);
  }

  @Transactional(readOnly = true)
  public List<UUID> participantIds(UUID conversationId) {
    return participants.findUserIdsByConversationId(conversationId);
  }

  @Transactional
  public void requireMessagingAllowed(UUID userId, UUID conversationId) {
    requireParticipant(userId, conversationId);
    List<UUID> participantIds = participants.findUserIdsByConversationId(conversationId);
    UUID peerId =
        participantIds.stream()
            .filter(participantId -> !participantId.equals(userId))
            .findFirst()
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.CONFLICT.value(),
                        ErrorCodes.CONFLICT,
                        "Direct conversation has invalid participants."));
    lockUserPair(userId, peerId);
    if (blockedEitherWay(userId, peerId)) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.USER_BLOCKED,
          "Message cannot be sent while blocked.");
    }
  }

  @Transactional(readOnly = true)
  public boolean canDeliver(UUID senderId, UUID recipientId) {
    return !blockedEitherWay(senderId, recipientId);
  }

  @Transactional(readOnly = true)
  public boolean isParticipant(UUID userId, UUID conversationId) {
    return participants.existsByConversationIdAndUserId(conversationId, userId);
  }

  public void requireParticipant(UUID userId, UUID conversationId) {
    if (!isParticipant(userId, conversationId)) {
      throw new DomainException(
          HttpStatus.NOT_FOUND.value(),
          ErrorCodes.RESOURCE_NOT_FOUND,
          "Conversation was not found.");
    }
  }

  private Conversation requireConversation(UUID conversationId) {
    return conversations
        .findById(conversationId)
        .orElseThrow(
            () ->
                new DomainException(
                    HttpStatus.NOT_FOUND.value(),
                    ErrorCodes.RESOURCE_NOT_FOUND,
                    "Conversation was not found."));
  }

  private ConversationResponse toResponse(Conversation conversation) {
    List<UUID> userIds = participants.findUserIdsByConversationId(conversation.getId());
    Map<UUID, User> usersById =
        users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, user -> user));
    Map<UUID, String> displayNames =
        profiles.findByUserIdIn(new ArrayList<>(usersById.keySet())).stream()
            .collect(Collectors.toMap(Profile::getUserId, Profile::getDisplayName));
    List<ConversationPeer> peers =
        userIds.stream()
            .map(
                userId -> {
                  User user = usersById.get(userId);
                  String username = user == null ? "unknown" : user.getUsername();
                  return new ConversationPeer(
                      userId, username, displayNames.getOrDefault(userId, username));
                })
            .toList();
    return new ConversationResponse(
        conversation.getId(), conversation.getType(), peers, conversation.getCreatedAt());
  }

  private boolean blockedEitherWay(UUID first, UUID second) {
    return blocks.existsByBlockerIdAndBlockedId(first, second)
        || blocks.existsByBlockerIdAndBlockedId(second, first);
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

  private User requirePeer(UUID userId) {
    return users
        .findById(userId)
        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
        .orElseThrow(
            () ->
                new DomainException(
                    HttpStatus.NOT_FOUND.value(),
                    ErrorCodes.RESOURCE_NOT_FOUND,
                    "User was not found."));
  }

  private void lockUserPair(UUID first, UUID second) {
    UUID low = first.compareTo(second) < 0 ? first : second;
    UUID high = first.compareTo(second) < 0 ? second : first;
    users
        .findByIdForUpdate(low)
        .orElseThrow(
            () ->
                new DomainException(
                    HttpStatus.NOT_FOUND.value(),
                    ErrorCodes.RESOURCE_NOT_FOUND,
                    "User was not found."));
    users
        .findByIdForUpdate(high)
        .orElseThrow(
            () ->
                new DomainException(
                    HttpStatus.NOT_FOUND.value(),
                    ErrorCodes.RESOURCE_NOT_FOUND,
                    "User was not found."));
  }
}
