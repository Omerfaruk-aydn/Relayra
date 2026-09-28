package com.relayra.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "communities")
public class Community {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "name", length = 100, nullable = false)
  private String name;

  @Column(name = "description", length = 1000)
  private String description;

  @Column(name = "icon_key")
  private String iconKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected Community() {}

  public Community(UUID id, UUID ownerId, String name) {
    this.id = id;
    this.ownerId = ownerId;
    this.name = name;
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

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getIconKey() {
    return iconKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void rename(String name, String description, String iconKey) {
    this.name = name;
    this.description = description;
    this.iconKey = iconKey;
  }

  public void transferOwnership(UUID newOwnerId) {
    this.ownerId = newOwnerId;
  }
}
