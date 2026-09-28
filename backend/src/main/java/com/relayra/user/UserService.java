package com.relayra.user;

import com.relayra.auth.RateLimiter;
import com.relayra.auth.RateLimitedException;
import com.relayra.auth.domain.Profile;
import com.relayra.auth.domain.User;
import com.relayra.auth.domain.UserStatus;
import com.relayra.auth.persistence.ProfileRepository;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.user.dto.PatchProfileRequest;
import com.relayra.user.dto.PublicUserProfileResponse;
import com.relayra.user.dto.UserProfileResponse;
import com.relayra.user.dto.UserSearchResult;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private static final int SEARCH_DEFAULT_LIMIT = 20;
  private static final int SEARCH_MAX_LIMIT = 50;
  private static final int MAX_FIELD_LENGTH = 512;
  private static final int MAX_TIMEZONE_LENGTH = 64;
  private static final int MAX_BIO_LENGTH = 500;
  private static final int MAX_DISPLAY_NAME_LENGTH = 64;

  private final UserRepository users;
  private final ProfileRepository profiles;
  private final RateLimiter rateLimiter;

  public UserService(UserRepository users, ProfileRepository profiles, RateLimiter rateLimiter) {
    this.users = users;
    this.profiles = profiles;
    this.rateLimiter = rateLimiter;
  }

  @Transactional(readOnly = true)
  public PublicUserProfileResponse getPublicProfile(UUID targetId) {
    User user =
        users
            .findById(targetId)
            .filter(u -> u.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "User was not found."));
    Profile profile = profiles.findByUserId(targetId).orElse(null);
    return new PublicUserProfileResponse(
        user.getId(),
        user.getUsername(),
        profile == null || profile.getDisplayName() == null
            ? user.getUsername()
            : profile.getDisplayName(),
        profile == null ? null : profile.getBio(),
        user.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public UserProfileResponse getOwnProfile(UUID userId) {
    User user = requireActiveUser(userId);
    Profile profile = requireProfile(userId);
    return toResponse(user, profile);
  }

  @Transactional
  public UserProfileResponse updateMyProfile(UUID userId, PatchProfileRequest request) {
    User user = requireActiveUser(userId);
    Profile profile = requireProfile(userId);
    applyIfPresent(request.displayName(), MAX_DISPLAY_NAME_LENGTH, profile::setDisplayName);
    applyIfPresent(request.bio(), MAX_BIO_LENGTH, profile::setBio);
    applyIfPresent(request.avatarKey(), MAX_FIELD_LENGTH, profile::setAvatarKey);
    applyIfPresent(request.bannerKey(), MAX_FIELD_LENGTH, profile::setBannerKey);
    applyIfPresent(request.timezone(), MAX_TIMEZONE_LENGTH, profile::setTimezone);
    return toResponse(user, profile);
  }

  @Transactional(readOnly = true)
  public List<UserSearchResult> search(UUID callerId, String query, Integer limit) {
    String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (q.length() < 2 || q.length() > 64) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Search query must be between 2 and 64 characters.");
    }
    int size = limit == null ? SEARCH_DEFAULT_LIMIT : limit;
    if (size < 1 || size > SEARCH_MAX_LIMIT) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Search limit must be between 1 and 50.");
    }
    requireActiveUser(callerId);
    rateLimiter.check(
        RateLimitedException.deviceKey("user-search", callerId), 60, Duration.ofMinutes(1));
    return users.searchActiveByUsernameFragment(
        UserStatus.ACTIVE, escapeLike(q), PageRequest.of(0, size));
  }

  private User requireActiveUser(UUID userId) {
    User user =
        users
            .findById(userId)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "User was not found."));
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(), ErrorCodes.ACCESS_DENIED, "Account is disabled.");
    }
    return user;
  }

  private Profile requireProfile(UUID userId) {
    return profiles
        .findByUserId(userId)
        .orElseThrow(
            () ->
                new DomainException(
                    500,
                    ErrorCodes.INTERNAL_ERROR,
                    "Profile is missing for this account."));
  }

  private UserProfileResponse toResponse(User user, Profile profile) {
    return new UserProfileResponse(
        user.getId(),
        user.getUsername(),
        profile.getDisplayName() == null ? user.getUsername() : profile.getDisplayName(),
        profile.getBio(),
        profile.getAvatarKey(),
        profile.getBannerKey(),
        profile.getTimezone(),
        user.getCreatedAt());
  }

  private void applyIfPresent(
      Optional<String> raw, int maxLength, java.util.function.Consumer<String> setter) {
    if (raw.isEmpty()) {
      return;
    }
    String value = trimToNull(raw.get());
    if (value != null && value.length() > maxLength) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Profile field exceeds maximum length.");
    }
    setter.accept(value);
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
