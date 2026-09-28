package com.relayra.realtime;

import com.relayra.realtime.dto.RealtimeEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MessageRealtimeBroadcaster {

  private final SimpMessagingTemplate messagingTemplate;

  public MessageRealtimeBroadcaster(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void broadcast(MessageChangedEvent event) {
    if (event.data().channelId() == null) {
      return;
    }
    messagingTemplate.convertAndSend(
        "/topic/channels/" + event.data().channelId() + "/messages",
        new RealtimeEvent<>(UUID.randomUUID(), event.type(), Instant.now(), event.data()));
  }
}
