package com.relayra.channel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "channels")
public class Channel {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Column(name = "name", length = 100, nullable = false)
  private String name;

  @Column(name = "description", length = 250)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", length = 32, nullable = false, updatable = false)
  private ChannelType type;

  @Column(name = "position", nullable = false)
  private int position;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected Channel() {}

  public Channel(
      UUID id, UUID communityId, String name, ChannelType type, int position) {
    this.id = id;
    this.communityId = communityId;
    this.name = name;
    this.type = type;
    this.position = position;
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

  public UUID getCommunityId() {
    return communityId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public ChannelType getType() {
    return type;
  }

  public int getPosition() {
    return position;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void update(String name, String description) {
    this.name = name;
    this.description = description;
  }

  public void moveTo(int position) {
    if (position < 0) {
      throw new IllegalArgumentException("Channel position cannot be negative.");
    }
    this.position = position;
  }
}
