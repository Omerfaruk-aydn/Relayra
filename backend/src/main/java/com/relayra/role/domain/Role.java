package com.relayra.role.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
    name = "roles",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_roles_community_name",
          columnNames = {"community_id", "name"})
    })
public class Role {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Column(name = "name", length = 64, nullable = false)
  private String name;

  @Column(name = "position", nullable = false)
  private int position;

  @Column(name = "color", length = 16)
  private String color;

  @Column(name = "managed", nullable = false)
  private boolean managed;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "permission", length = 64, nullable = false)
  private Set<Permission> permissions = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Role() {}

  public Role(UUID id, UUID communityId, String name, int position, boolean managed) {
    this.id = id;
    this.communityId = communityId;
    this.name = name;
    this.position = position;
    this.managed = managed;
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

  public int getPosition() {
    return position;
  }

  public String getColor() {
    return color;
  }

  public boolean isManaged() {
    return managed;
  }

  public Set<Permission> getPermissions() {
    return Set.copyOf(permissions);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void update(String name, String color, Set<Permission> permissions) {
    this.name = name;
    this.color = color;
    this.permissions.clear();
    this.permissions.addAll(permissions);
  }

  public void moveTo(int position) {
    if (position < 0) {
      throw new IllegalArgumentException("Role position cannot be negative.");
    }
    this.position = position;
  }
}
