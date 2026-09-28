package com.relayra.notification;

import com.relayra.notification.dto.NotificationEventData;
import java.util.UUID;

public record NotificationChangedEventForPush(String type, UUID targetUserId, NotificationEventData data) {}
