package com.relayra.common.error;

public final class ErrorCodes {

  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  public static final String AUTHENTICATION_REQUIRED = "AUTHENTICATION_REQUIRED";
  public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
  public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
  public static final String TOKEN_INVALID = "TOKEN_INVALID";
  public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
  public static final String ACCESS_DENIED = "ACCESS_DENIED";
  public static final String INSUFFICIENT_PERMISSION = "INSUFFICIENT_PERMISSION";
  public static final String CONFLICT = "CONFLICT";
  public static final String DUPLICATE_RESOURCE = "DUPLICATE_RESOURCE";
  public static final String USER_BLOCKED = "USER_BLOCKED";
  public static final String USER_BANNED = "USER_BANNED";
  public static final String INVITE_INVALID = "INVITE_INVALID";
  public static final String INVITE_EXPIRED = "INVITE_EXPIRED";
  public static final String INVITE_EXHAUSTED = "INVITE_EXHAUSTED";
  public static final String MESSAGE_DELETED = "MESSAGE_DELETED";
  public static final String RATE_LIMITED = "RATE_LIMITED";
  public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
  public static final String FILE_TYPE_NOT_ALLOWED = "FILE_TYPE_NOT_ALLOWED";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  private ErrorCodes() {}
}
