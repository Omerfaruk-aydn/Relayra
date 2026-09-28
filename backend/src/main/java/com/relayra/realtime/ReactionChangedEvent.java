package com.relayra.realtime;

import com.relayra.reaction.dto.ReactionEventData;

public record ReactionChangedEvent(String type, ReactionEventData data) {}
