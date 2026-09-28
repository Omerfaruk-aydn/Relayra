package com.relayra.realtime;

import com.relayra.message.dto.MessageResponse;

public record MessageChangedEvent(String type, MessageResponse data) {}
