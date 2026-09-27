package com.relayra.common.error;

import java.time.Instant;
import java.util.Map;

public record ApiError(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    String requestId,
    Map<String, String> errors) {

  public static ApiError of(
      int status, String code, String message, String path, String requestId) {
    return new ApiError(Instant.now(), status, code, message, path, requestId, null);
  }

  public static ApiError of(
      int status,
      String code,
      String message,
      String path,
      String requestId,
      Map<String, String> errors) {
    return new ApiError(Instant.now(), status, code, message, path, requestId, errors);
  }
}
