package com.relayra.auth;

import com.relayra.auth.domain.Profile;
import com.relayra.auth.domain.RefreshToken;
import com.relayra.auth.domain.User;
import com.relayra.auth.domain.UserStatus;
import com.relayra.auth.dto.AuthResponse;
import com.relayra.auth.dto.LoginRequest;
import com.relayra.auth.dto.RegisterRequest;
import com.relayra.auth.dto.UserSummary;
import com.relayra.auth.persistence.ProfileRepository;
import com.relayra.auth.persistence.RefreshTokenRepository;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final int MAX_FAILED_LOOKUPS = 3;

  private final UserRepository users;
  private final ProfileRepository profiles;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthProperties properties;
  private final RateLimiter rateLimiter;
  private final SecureRandom secureRandom = new SecureRandom();

  public AuthService(
      UserRepository users,
      ProfileRepository profiles,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      AuthProperties properties,
      RateLimiter rateLimiter) {
    this.users = users;
    this.profiles = profiles;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.properties = properties;
    this.rateLimiter = rateLimiter;
  }

  @Transactional
  public RegistrationResult register(RegisterRequest request, String ip, String userAgent) {
    rateLimiter.check(
        RateLimitedException.ipKey("register", ip), 3, Duration.ofHours(1));
    String username = request.username().trim();
    String email = request.email().trim();
    String usernameNormalized = username.toLowerCase(Locale.ROOT);
    String emailNormalized = email.toLowerCase(Locale.ROOT);

    if (users.existsByUsernameNormalized(usernameNormalized)
        || users.existsByEmailNormalized(emailNormalized)) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.DUPLICATE_RESOURCE,
          "Username or email is already taken.");
    }

    UUID userId = UUID.randomUUID();
    User user =
        new User(
            userId,
            username,
            usernameNormalized,
            email,
            emailNormalized,
            passwordEncoder.encode(request.password()));
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.DUPLICATE_RESOURCE,
          "Username or email is already taken.");
    }
    profiles.save(new Profile(UUID.randomUUID(), userId, username));
    return issueSession(user, ip, userAgent);
  }

  @Transactional
  public RegistrationResult login(LoginRequest request, String ip, String userAgent) {
    rateLimiter.check(
        RateLimitedException.ipKey("login", ip), 5, Duration.ofMinutes(1));
    String identifier = request.identifier().trim();
    String normalized = identifier.toLowerCase(Locale.ROOT);
    Optional<User> user =
        identifier.contains("@")
            ? users.findByEmailNormalized(normalized)
            : users.findByUsernameNormalized(normalized);

    boolean failed = user.isEmpty() || user.get().getStatus() != UserStatus.ACTIVE;
    if (!failed) {
      failed = !passwordEncoder.matches(request.password(), user.get().getPasswordHash());
    }
    if (failed) {
      throw new DomainException(
          HttpStatus.UNAUTHORIZED.value(),
          ErrorCodes.INVALID_CREDENTIALS,
          "Invalid credentials.");
    }
    User authenticated = user.get();
    authenticated.markSeen();
    users.save(authenticated);
    return issueSession(authenticated, ip, userAgent);
  }

  @Transactional
  public RegistrationResult refresh(
      String refreshToken, String ip, String userAgent) {
    rateLimiter.check(
        RateLimitedException.ipKey("refresh", ip),
        30,
        Duration.ofMinutes(1));
    String hash = TokenHasher.sha256Hex(refreshToken);
    RefreshToken stored =
        refreshTokens
            .findByTokenHash(hash)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.UNAUTHORIZED.value(),
                        ErrorCodes.TOKEN_INVALID,
                        "Refresh token is invalid."));
    Instant now = Instant.now();
    if (stored.isRevoked()) {
      revokeFamily(stored.getTokenFamilyId(), now);
      throw new DomainException(
          HttpStatus.UNAUTHORIZED.value(),
          ErrorCodes.TOKEN_INVALID,
          "Refresh token is invalid.");
    }
    if (stored.isExpired(now)) {
      stored.revoke(now);
      throw new DomainException(
          HttpStatus.UNAUTHORIZED.value(),
          ErrorCodes.TOKEN_EXPIRED,
          "Refresh token has expired.");
    }
    User user =
        users
            .findById(stored.getUserId())
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.UNAUTHORIZED.value(),
                        ErrorCodes.TOKEN_INVALID,
                        "Refresh token is invalid."));
    if (user.getStatus() != UserStatus.ACTIVE) {
      revokeFamily(stored.getTokenFamilyId(), now);
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(), ErrorCodes.ACCESS_DENIED, "Account is disabled.");
    }
    stored.revoke(UUID.randomUUID(), now);
    return issueSession(user, ip, userAgent, stored.getTokenFamilyId());
  }

  @Transactional
  public void logout(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      return;
    }
    refreshTokens
        .findByTokenHash(TokenHasher.sha256Hex(refreshToken))
        .ifPresent(token -> token.revoke(Instant.now()));
  }

  @Transactional
  public void logoutAll(UUID userId) {
    Instant now = Instant.now();
    refreshTokens.findAll().stream()
        .filter(token -> token.getUserId().equals(userId) && !token.isRevoked())
        .forEach(token -> token.revoke(now));
  }

  @Transactional(readOnly = true)
  public UserSummary me(UUID userId) {
    User user =
        users
            .findById(userId)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "User was not found."));
    String displayName =
        profiles
            .findByUserId(userId)
            .map(Profile::getDisplayName)
            .orElse(user.getUsername());
    return new UserSummary(user.getId(), user.getUsername(), displayName);
  }

  private RegistrationResult issueSession(User user, String ip, String userAgent) {
    return issueSession(user, ip, userAgent, UUID.randomUUID());
  }

  private RegistrationResult issueSession(
      User user, String ip, String userAgent, UUID familyId) {
    String refreshToken = randomToken();
    RefreshToken stored =
        new RefreshToken(
            UUID.randomUUID(),
            user.getId(),
            TokenHasher.sha256Hex(refreshToken),
            familyId,
            Instant.now().plusSeconds(properties.refreshTokenTtlSeconds()),
            hashOrNull(userAgent),
            hashOrNull(ip));
    refreshTokens.save(stored);
    String displayName =
        profiles
            .findByUserId(user.getId())
            .map(Profile::getDisplayName)
            .orElse(user.getUsername());
    AuthResponse response =
        new AuthResponse(
            jwtService.generateAccessToken(user.getId()),
            properties.accessTokenTtlSeconds(),
            new UserSummary(user.getId(), user.getUsername(), displayName));
    return new RegistrationResult(response, refreshToken);
  }

  private void revokeFamily(UUID familyId, Instant now) {
    refreshTokens.findByTokenFamilyIdAndRevokedAtIsNull(familyId).stream()
        .limit(MAX_FAILED_LOOKUPS + 100L)
        .forEach(token -> token.revoke(now));
  }

  private String randomToken() {
    byte[] bytes = new byte[48];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hashOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return TokenHasher.sha256Hex(value);
  }

  public record RegistrationResult(AuthResponse response, String refreshToken) {}
}
