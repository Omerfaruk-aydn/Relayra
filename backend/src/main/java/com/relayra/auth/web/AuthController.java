package com.relayra.auth.web;

import com.relayra.auth.AuthProperties;
import com.relayra.auth.AuthService;
import com.relayra.auth.AuthService.RegistrationResult;
import com.relayra.auth.dto.AuthResponse;
import com.relayra.auth.dto.LoginRequest;
import com.relayra.auth.dto.RegisterRequest;
import com.relayra.auth.dto.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  public static final String REFRESH_COOKIE = "relayra_refresh";

  private final AuthService authService;
  private final AuthProperties properties;

  public AuthController(AuthService authService, AuthProperties properties) {
    this.authService = authService;
    this.properties = properties;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
    RegistrationResult result =
        authService.register(request, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
    return withRefreshCookie(result, HttpStatus.CREATED);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    RegistrationResult result =
        authService.login(request, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
    return withRefreshCookie(result, HttpStatus.OK);
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
      HttpServletRequest httpRequest) {
    RegistrationResult result =
        authService.refresh(refreshToken == null ? "" : refreshToken,
            httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
    return withRefreshCookie(result, HttpStatus.OK);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
    authService.logout(refreshToken);
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
  }

  @GetMapping("/me")
  public ResponseEntity<UserSummary> me(Authentication authentication) {
    UUID userId = (UUID) authentication.getPrincipal();
    return ResponseEntity.ok(authService.me(userId));
  }

  private ResponseEntity<AuthResponse> withRefreshCookie(
      RegistrationResult result, HttpStatus status) {
    ResponseCookie cookie =
        ResponseCookie.from(REFRESH_COOKIE, result.refreshToken())
            .httpOnly(true)
            .secure(properties.secureCookies())
            .sameSite("Lax")
            .path("/api/v1/auth")
            .maxAge(Duration.ofSeconds(properties.refreshTokenTtlSeconds()))
            .build();
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(result.response());
  }

  private ResponseCookie clearCookie() {
    return ResponseCookie.from(REFRESH_COOKIE, "")
        .httpOnly(true)
        .secure(properties.secureCookies())
        .sameSite("Lax")
        .path("/api/v1/auth")
        .maxAge(0)
        .build();
  }
}
