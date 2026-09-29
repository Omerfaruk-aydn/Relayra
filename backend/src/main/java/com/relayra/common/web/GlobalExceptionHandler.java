package com.relayra.common.web;

import com.relayra.auth.RateLimitedException;
import com.relayra.common.error.ApiError;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiError> handleDomain(DomainException ex, HttpServletRequest request) {
    return ResponseEntity.status(ex.getStatus())
        .body(
            ApiError.of(
                ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI(),
                currentRequestId()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    e -> e.getField(),
                    e -> e.getDefaultMessage() == null ? "Invalid value." : e.getDefaultMessage(),
                    (first, second) -> first));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                ErrorCodes.VALIDATION_FAILED,
                "Request validation failed.",
                request.getRequestURI(),
                currentRequestId(),
                errors));
  }

  @ExceptionHandler(RateLimitedException.class)
  public ResponseEntity<ApiError> handleRateLimited(
      RateLimitedException ex, HttpServletRequest request) {
    return ResponseEntity.status(ex.getStatus())
        .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
        .body(
            ApiError.of(
                ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI(),
                currentRequestId()));
  }

  @ExceptionHandler({
    ObjectOptimisticLockingFailureException.class,
    jakarta.persistence.OptimisticLockException.class,
    IllegalStateException.class
  })
  public ResponseEntity<ApiError> handleConflict(Exception ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ApiError.of(
                HttpStatus.CONFLICT.value(),
                ErrorCodes.CONFLICT,
                "Resource was modified concurrently. Please retry.",
                request.getRequestURI(),
                currentRequestId()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error(
        "Unhandled error for {} {}: {}",
        request.getMethod(),
        request.getRequestURI(),
        ex.toString(),
        ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ErrorCodes.INTERNAL_ERROR,
                "An unexpected error occurred.",
                request.getRequestURI(),
                currentRequestId()));
  }

  private String currentRequestId() {
    String requestId = MDC.get(RequestIdFilter.REQUEST_ID_KEY);
    return requestId == null ? "unknown" : requestId;
  }
}
