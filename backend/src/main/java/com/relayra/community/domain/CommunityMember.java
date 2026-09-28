package com.relayra.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "community_members",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_community_members_pair",
          columnNames = {"community_id", "user_id"})
    })
public class CommunityMember {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "nickname", length = 64)
  private String nickname;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 32, nullable = false)
  private MemberStatus status = MemberStatus.ACTIVE;

  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  protected CommunityMember() {}

  public CommunityMember(UUID id, UUID communityId, UUID userId) {
    this.id = id;
    this.communityId = communityId;
    this.userId = userId;
  }

  @PrePersist
  void onCreate() {
    this.joinedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getCommunityId() {
    return communityId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getNickname() {
    return nickname;
  }

  public MemberStatus getStatus() {
    return status;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public void disable() {
    this.status = MemberStatus.DISABLED;
  }
}
