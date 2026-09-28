package com.relayra.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profiles")
public class Profile {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, unique = true, updatable = false)
  private UUID userId;

  @Column(name = "display_name", length = 64)
  private String displayName;

  @Column(name = "bio", length = 500)
  private String bio;

  @Column(name = "avatar_key", length = 512)
  private String avatarKey;

  @Column(name = "banner_key", length = 512)
  private String bannerKey;

  @Column(name = "timezone", length = 64)
  private String timezone;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Profile() {}

  public Profile(UUID id, UUID userId, String displayName) {
    this.id = id;
    this.userId = userId;
    this.displayName = displayName;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getBio() {
    return bio;
  }

  public String getAvatarKey() {
    return avatarKey;
  }

  public String getBannerKey() {
    return bannerKey;
  }

  public String getTimezone() {
    return timezone;
  }

  public void update(
      String displayName, String bio, String avatarKey, String bannerKey, String timezone) {
    this.displayName = displayName;
    this.bio = bio;
    this.avatarKey = avatarKey;
    this.bannerKey = bannerKey;
    this.timezone = timezone;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public void setBio(String bio) {
    this.bio = bio;
  }

  public void setAvatarKey(String avatarKey) {
    this.avatarKey = avatarKey;
  }

  public void setBannerKey(String bannerKey) {
    this.bannerKey = bannerKey;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }
}
