package com.relayra.realtime.dto;

public record RealtimeError(String type, String requestId, String code, String message) {

  public static RealtimeError of(String requestId, String code, String message) {
    return new RealtimeError("ERROR", requestId, code, message);
  }
}
