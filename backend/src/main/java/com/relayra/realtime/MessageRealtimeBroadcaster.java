package com.relayra.realtime;

import com.relayra.conversation.ConversationService;
import com.relayra.reaction.dto.ReactionEventData;
import com.relayra.realtime.dto.RealtimeEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MessageRealtimeBroadcaster {

  private final SimpMessagingTemplate messagingTemplate;
  private final ConversationService conversations;

  public MessageRealtimeBroadcaster(
      SimpMessagingTemplate messagingTemplate, ConversationService conversations) {
    this.messagingTemplate = messagingTemplate;
    this.conversations = conversations;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void broadcast(MessageChangedEvent event) {
    if (event.data().channelId() != null) {
      messagingTemplate.convertAndSend(
          "/topic/channels/" + event.data().channelId() + "/messages",
          new RealtimeEvent<>(UUID.randomUUID(), event.type(), Instant.now(), event.data()));
      return;
    }
    if (event.data().conversationId() == null) {
      return;
    }
    RealtimeEvent<?> envelope =
        new RealtimeEvent<>(UUID.randomUUID(), event.type(), Instant.now(), event.data());
    UUID senderId = event.data().author().id();
    List<UUID> recipients = conversations.participantIds(event.data().conversationId());
    for (UUID recipient : recipients) {
      if (conversations.canDeliver(senderId, recipient)) {
        messagingTemplate.convertAndSendToUser(recipient.toString(), "/queue/messages", envelope);
      }
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void broadcastReaction(ReactionChangedEvent event) {
    ReactionEventData data = event.data();
    RealtimeEvent<?> envelope =
        new RealtimeEvent<>(UUID.randomUUID(), event.type(), Instant.now(), data);
    if (data.channelId() != null) {
      messagingTemplate.convertAndSend(
          "/topic/channels/" + data.channelId() + "/messages", envelope);
      return;
    }
    if (data.conversationId() == null) {
      return;
    }
    List<UUID> recipients = conversations.participantIds(data.conversationId());
    for (UUID recipient : recipients) {
      if (conversations.canDeliver(data.user().id(), recipient)) {
        messagingTemplate.convertAndSendToUser(recipient.toString(), "/queue/messages", envelope);
      }
    }
  }
}
