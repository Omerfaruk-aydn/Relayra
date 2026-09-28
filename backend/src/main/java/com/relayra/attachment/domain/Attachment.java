package com.relayra.attachment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachments")
public class Attachment {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "message_id")
  private UUID messageId;

  @Column(name = "uploaded_by", nullable = false, updatable = false)
  private UUID uploadedBy;

  @Column(name = "community_id", updatable = false)
  private UUID communityId;

  @Column(name = "channel_id", updatable = false)
  private UUID channelId;

  @Column(name = "conversation_id", updatable = false)
  private UUID conversationId;

  @Column(name = "original_filename", length = 255, nullable = false, updatable = false)
  private String originalFilename;

  @Column(name = "storage_key", length = 512, nullable = false, unique = true, updatable = false)
  private String storageKey;

  @Column(name = "mime_type", length = 255, nullable = false, updatable = false)
  private String mimeType;

  @Column(name = "size_bytes", nullable = false, updatable = false)
  private long sizeBytes;

  @Column(name = "sha256", length = 64, updatable = false)
  private String sha256;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Attachment() {}

  public Attachment(UUID id, UUID uploadedBy, String originalFilename, String storageKey) {
    this.id = id;
    this.uploadedBy = uploadedBy;
    this.originalFilename = originalFilename;
    this.storageKey = storageKey;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getMessageId() {
    return messageId;
  }

  public UUID getUploadedBy() {
    return uploadedBy;
  }

  public UUID getCommunityId() {
    return communityId;
  }

  public UUID getChannelId() {
    return channelId;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getMimeType() {
    return mimeType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getSha256() {
    return sha256;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void describe(String mimeType, long sizeBytes, String sha256) {
    this.mimeType = mimeType;
    this.sizeBytes = sizeBytes;
    this.sha256 = sha256;
  }

  public void scope(UUID communityId, UUID channelId, UUID conversationId) {
    this.communityId = communityId;
    this.channelId = channelId;
    this.conversationId = conversationId;
  }

  public void attachTo(UUID messageId) {
    this.messageId = messageId;
  }
}
