package com.relayra.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_users_username_normalized", columnNames = "username_normalized"),
      @UniqueConstraint(name = "uq_users_email_normalized", columnNames = "email_normalized")
    })
public class User {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "username", length = 32, nullable = false)
  private String username;

  @Column(name = "username_normalized", length = 32, nullable = false)
  private String usernameNormalized;

  @Column(name = "email", length = 320, nullable = false)
  private String email;

  @Column(name = "email_normalized", length = 320, nullable = false)
  private String emailNormalized;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 32, nullable = false)
  private UserStatus status = UserStatus.ACTIVE;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected User() {}

  public User(
      UUID id,
      String username,
      String usernameNormalized,
      String email,
      String emailNormalized,
      String passwordHash) {
    this.id = id;
    this.username = username;
    this.usernameNormalized = usernameNormalized;
    this.email = email;
    this.emailNormalized = emailNormalized;
    this.passwordHash = passwordHash;
    this.status = UserStatus.ACTIVE;
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

  public String getUsername() {
    return username;
  }

  public String getUsernameNormalized() {
    return usernameNormalized;
  }

  public String getEmail() {
    return email;
  }

  public String getEmailNormalized() {
    return emailNormalized;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public void markSeen() {
    this.lastSeenAt = Instant.now();
  }

  public void disable() {
    this.status = UserStatus.DISABLED;
  }
}
