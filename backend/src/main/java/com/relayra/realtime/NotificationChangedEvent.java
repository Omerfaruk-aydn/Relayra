package com.relayra.realtime;

import com.relayra.notification.dto.NotificationEventData;

public record NotificationChangedEvent(String type, NotificationEventData data) {}
