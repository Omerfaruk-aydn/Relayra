package com.relayra.attachment;

import com.relayra.attachment.domain.Attachment;
import com.relayra.attachment.dto.AttachmentResponse;
import com.relayra.attachment.dto.UploadResponse;
import com.relayra.attachment.persistence.AttachmentRepository;
import com.relayra.auth.RateLimitedException;
import com.relayra.auth.RateLimiter;
import com.relayra.auth.domain.User;
import com.relayra.auth.domain.UserStatus;
import com.relayra.auth.persistence.UserRepository;
import com.relayra.channel.persistence.ChannelRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import com.relayra.conversation.ConversationService;
import com.relayra.message.domain.Message;
import com.relayra.message.persistence.MessageRepository;
import com.relayra.role.PermissionService;
import com.relayra.role.domain.Permission;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {

  static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

  private static final Set<String> BLOCKED_MIME_PREFIXES =
      Set.of("application/x-msdownload", "application/x-msdos-program");
  private static final Set<String> BLOCKED_MIME_EXACT =
      Set.of(
          "application/x-sh",
          "application/x-shockwave-flash",
          "application/x-executable",
          "application/x-elf",
          "application/x-mach-binary",
          "application/vnd.microsoft.portable-executable");
  private static final Set<String> BLOCKED_EXTENSIONS =
      Set.of(
          "exe", "dll", "bat", "cmd", "com", "scr", "ps1", "vbs", "js", "jar", "msi", "sh", "apk",
          "dmg", "app", "run", "bin", "elf", "so", "dylib", "sys", "drv", "html", "htm",
          "xhtml", "svg", "swf", "hta", "php", "phtml", "php5", "shtml", "xht");

  private static final Map<String, String> SIGNATURE_MIME =
      Map.ofEntries(
          Map.entry("89504E47", "image/png"),
          Map.entry("FFD8FF", "image/jpeg"),
          Map.entry("47494638", "image/gif"),
          Map.entry("25504446", "application/pdf"),
          Map.entry("504B0304", "application/zip"),
          Map.entry("504B0506", "application/zip"),
          Map.entry("504B0708", "application/zip"),
          Map.entry("52617221", "application/x-rar-compressed"),
          Map.entry("377ABCAF", "application/x-7z-compressed"),
          Map.entry("1F8B08", "application/gzip"),
          Map.entry("425A68", "application/x-bzip2"),
          Map.entry("4D5A", "application/x-msdownload"),
          Map.entry("7F454C46", "application/x-elf"),
          Map.entry("FEEDFACE", "application/x-mach-binary"),
          Map.entry("FEEDFACF", "application/x-mach-binary"),
          Map.entry("CEFAEDFE", "application/x-mach-binary"),
          Map.entry("CFFAEDFE", "application/x-mach-binary"),
          Map.entry("CAFEBABE", "application/x-executable"));

  private static final Set<String> BLOCKED_ACTIVE_MIME =
      Set.of("text/html", "image/svg+xml", "application/xhtml+xml", "text/xml", "application/xml");

  private static final Set<String> SAFE_DECLARED_MIME =      Set.of(
          "image/png",
          "image/jpeg",
          "image/gif",
          "application/pdf",
          "text/plain",
          "application/zip",
          "application/x-rar-compressed",
          "application/x-7z-compressed",
          "application/gzip",
          "application/x-bzip2",
          "audio/mpeg",
          "audio/ogg",
          "video/mp4",
          "application/octet-stream");

  private final AttachmentRepository attachments;
  private final MessageRepository messages;
  private final ChannelRepository channels;
  private final ConversationService conversations;
  private final UserRepository users;
  private final PermissionService permissions;
  private final RateLimiter rateLimiter;
  private final LocalObjectStore store;

  public AttachmentService(
      AttachmentRepository attachments,
      MessageRepository messages,
      ChannelRepository channels,
      ConversationService conversations,
      UserRepository users,
      PermissionService permissions,
      RateLimiter rateLimiter,
      LocalObjectStore store) {
    this.attachments = attachments;
    this.messages = messages;
    this.channels = channels;
    this.conversations = conversations;
    this.users = users;
    this.permissions = permissions;
    this.rateLimiter = rateLimiter;
    this.store = store;
  }

  @Transactional
  public UploadResponse upload(
      UUID callerId, UUID channelId, UUID conversationId, MultipartFile file) {
    requireActive(callerId);
    UUID communityId = requireUploadScope(callerId, channelId, conversationId);
    rateLimiter.check(
        RateLimitedException.deviceKey("upload", callerId), 20, Duration.ofHours(1));
    if (file == null || file.isEmpty()) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "File must not be empty.");
    }
    if (file.getSize() > MAX_FILE_BYTES) {
      throw new DomainException(
          HttpStatus.PAYLOAD_TOO_LARGE.value(),
          ErrorCodes.FILE_TOO_LARGE,
          "File must be at most 10 MB.");
    }
    String originalFilename = cleanFilename(file.getOriginalFilename());
    String extension = extensionOf(originalFilename);
    if (BLOCKED_EXTENSIONS.contains(extension)) {
      throw new DomainException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
          ErrorCodes.FILE_TYPE_NOT_ALLOWED,
          "File type is not allowed.");
    }
    byte[] bytes = readBytes(file);
    if (bytes.length > MAX_FILE_BYTES) {
      throw new DomainException(
          HttpStatus.PAYLOAD_TOO_LARGE.value(),
          ErrorCodes.FILE_TOO_LARGE,
          "File must be at most 10 MB.");
    }
    String detected = detectMime(bytes);
    String declared = file.getContentType() == null ? null : file.getContentType().toLowerCase(Locale.ROOT);
    String mimeType = resolveMime(detected, declared, extension, originalFilename);
    String storageKey =
        "u/" + callerId + "/" + UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
    String sha256 = sha256(bytes);
    store.put(storageKey, new java.io.ByteArrayInputStream(bytes), bytes.length);
    Attachment attachment = new Attachment(UUID.randomUUID(), callerId, originalFilename, storageKey);
    attachment.describe(mimeType, bytes.length, sha256);
    attachment.scope(communityId, channelId, conversationId);
    try {
      Attachment saved = attachments.saveAndFlush(attachment);
      return new UploadResponse(
          saved.getId(),
          saved.getOriginalFilename(),
          saved.getMimeType(),
          saved.getSizeBytes(),
          saved.getCreatedAt());
    } catch (RuntimeException failure) {
      store.delete(storageKey);
      throw failure;
    }
  }

  @Transactional
  public List<AttachmentResponse> finalizeAttachments(
      UUID callerId, UUID messageId, List<UUID> attachmentIds) {
    requireActive(callerId);
    if (attachmentIds == null || attachmentIds.isEmpty() || attachmentIds.size() > 10) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "attachmentIds must contain between 1 and 10 attachments.");
    }
    if (attachmentIds.stream().distinct().count() != attachmentIds.size()) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "attachmentIds must not contain duplicates.");
    }
    Message message =
        messages
            .findByIdForUpdate(messageId)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "Message was not found."));
    if (!message.getAuthorId().equals(callerId)) {
      throw new DomainException(
          HttpStatus.FORBIDDEN.value(),
          ErrorCodes.INSUFFICIENT_PERMISSION,
          "Only the author can attach files to this message.");
    }
    if (message.getDeletedAt() != null) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.MESSAGE_DELETED,
          "Attachments cannot be added to a deleted message.");
    }
    requireAttachPermission(callerId, message);
    List<AttachmentResponse> linked =
        attachmentIds.stream()
            .map(
                attachmentId -> {
                  Attachment attachment =
                      attachments
                          .findByIdForUpdate(attachmentId)
                          .orElseThrow(
                              () ->
                                  new DomainException(
                                      HttpStatus.NOT_FOUND.value(),
                                      ErrorCodes.RESOURCE_NOT_FOUND,
                                      "Attachment was not found."));
                  if (!attachment.getUploadedBy().equals(callerId)) {
                    throw new DomainException(
                        HttpStatus.FORBIDDEN.value(),
                        ErrorCodes.INSUFFICIENT_PERMISSION,
                        "You can only attach your own uploads.");
                  }
                  if (attachment.getMessageId() != null && !attachment.getMessageId().equals(messageId)) {
                    throw new DomainException(
                        HttpStatus.CONFLICT.value(),
                        ErrorCodes.CONFLICT,
                        "Attachment is already linked to another message.");
                  }
                  if (!sameScope(attachment, message)) {
                    throw new DomainException(
                        HttpStatus.BAD_REQUEST.value(),
                        ErrorCodes.VALIDATION_FAILED,
                        "Attachment scope does not match the message.");
                  }
                  attachment.attachTo(messageId);
                  attachments.saveAndFlush(attachment);
                  return toResponse(attachment);
                })
            .toList();
    attachments.flush();
    return linked;
  }

  @Transactional(readOnly = true)
  public Resource download(UUID callerId, UUID attachmentId) {
    return downloadChecked(callerId, attachmentId).content();
  }

  @Transactional(readOnly = true)
  public CheckedDownload downloadChecked(UUID callerId, UUID attachmentId) {
    requireActive(callerId);
    Attachment attachment =
        attachments
            .findById(attachmentId)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "Attachment was not found."));
    if (attachment.getMessageId() == null) {
      if (!attachment.getUploadedBy().equals(callerId)) {
        throw new DomainException(
            HttpStatus.NOT_FOUND.value(),
            ErrorCodes.RESOURCE_NOT_FOUND,
            "Attachment was not found.");
      }
      return new CheckedDownload(openContent(attachment), toResponse(attachment));
    }
    Message message =
        messages
            .findById(attachment.getMessageId())
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "Attachment was not found."));
    if (message.getDeletedAt() != null) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.MESSAGE_DELETED,
          "Deleted message attachments are unavailable.");
    }
    requireMessageScopeAccess(callerId, message);
    return new CheckedDownload(openContent(attachment), toResponse(attachment));
  }

  public record CheckedDownload(Resource content, AttachmentResponse metadata) {}

  private Resource openContent(Attachment attachment) {
    try {
      return new InputStreamResource(store.open(attachment.getStorageKey()));
    } catch (IllegalStateException missing) {
      throw new DomainException(
          HttpStatus.NOT_FOUND.value(),
          ErrorCodes.RESOURCE_NOT_FOUND,
          "Attachment content is unavailable.");
    }
  }

  @Transactional(readOnly = true)
  public AttachmentResponse metadata(UUID callerId, UUID attachmentId) {
    requireActive(callerId);
    Attachment attachment =
        attachments
            .findById(attachmentId)
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "Attachment was not found."));
    if (attachment.getMessageId() == null) {
      if (!attachment.getUploadedBy().equals(callerId)) {
        throw new DomainException(
            HttpStatus.NOT_FOUND.value(),
            ErrorCodes.RESOURCE_NOT_FOUND,
            "Attachment was not found.");
      }
      return toResponse(attachment);
    }
    Message message =
        messages
            .findById(attachment.getMessageId())
            .orElseThrow(
                () ->
                    new DomainException(
                        HttpStatus.NOT_FOUND.value(),
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        "Attachment was not found."));
    if (message.getDeletedAt() != null) {
      throw new DomainException(
          HttpStatus.CONFLICT.value(),
          ErrorCodes.MESSAGE_DELETED,
          "Deleted message attachments are unavailable.");
    }
    requireMessageScopeAccess(callerId, message);
    return toResponse(attachment);
  }

  @Transactional
  public void deleteOnMessageDelete(UUID messageId) {
    List<Attachment> linked = attachments.findByMessageId(messageId);
    for (Attachment attachment : linked) {
      try {
        store.delete(attachment.getStorageKey());
      } catch (RuntimeException failure) {
        org.slf4j.LoggerFactory.getLogger(AttachmentService.class)
            .warn("Attachment bytes could not be deleted for {}: {}", attachment.getId(), failure.toString());
      }
    }
  }

  private void requireAttachPermission(UUID callerId, Message message) {
    if (message.getChannelId() != null) {
      var channel =
          channels
              .findById(message.getChannelId())
              .orElseThrow(
                  () ->
                      new DomainException(
                          HttpStatus.NOT_FOUND.value(),
                          ErrorCodes.RESOURCE_NOT_FOUND,
                          "Message was not found."));
      permissions.require(callerId, channel.getCommunityId(), Permission.ATTACH_FILES);
      return;
    }
    conversations.requireMessagingAllowed(callerId, message.getConversationId());
  }

  private UUID requireUploadScope(UUID callerId, UUID channelId, UUID conversationId) {
    if ((channelId == null) == (conversationId == null)) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Exactly one of channelId or conversationId is required.");
    }
    if (channelId != null) {
      var channel =
          channels
              .findById(channelId)
              .orElseThrow(
                  () ->
                      new DomainException(
                          HttpStatus.NOT_FOUND.value(),
                          ErrorCodes.RESOURCE_NOT_FOUND,
                          "Channel was not found."));
      permissions.require(callerId, channel.getCommunityId(), Permission.ATTACH_FILES);
      return channel.getCommunityId();
    }
    conversations.requireMessagingAllowed(callerId, conversationId);
    return null;
  }

  private void requireMessageScopeAccess(UUID callerId, Message message) {
    if (message.getChannelId() != null) {
      var channel =
          channels
              .findById(message.getChannelId())
              .orElseThrow(
                  () ->
                      new DomainException(
                          HttpStatus.NOT_FOUND.value(),
                          ErrorCodes.RESOURCE_NOT_FOUND,
                          "Message was not found."));
      permissions.require(callerId, channel.getCommunityId(), Permission.VIEW_CHANNEL);
      return;
    }
    conversations.requireParticipant(callerId, message.getConversationId());
  }

  private boolean sameScope(Attachment attachment, Message message) {
    if (message.getChannelId() != null) {
      return message.getChannelId().equals(attachment.getChannelId());
    }
    return message.getConversationId() != null
        && message.getConversationId().equals(attachment.getConversationId());
  }

  private String cleanFilename(String raw) {
    String name = raw == null ? "" : raw.replace('\\', '/').trim();
    if (name.contains("/")) {
      name = name.substring(name.lastIndexOf('/') + 1);
    }
    name = name.trim();
    if (name.isEmpty() || name.length() > 255 || name.equals(".") || name.equals("..")) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "Filename must be between 1 and 255 characters.");
    }
    return name;
  }

  private String extensionOf(String filename) {
    String ext = StringUtils.getFilenameExtension(filename);
    return ext == null ? "" : ext.toLowerCase(Locale.ROOT);
  }

  private byte[] readBytes(MultipartFile file) {
    try (InputStream in = file.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      long total = 0;
      int read;
      while ((read = in.read(buffer)) != -1) {
        total += read;
        if (total > MAX_FILE_BYTES + 1) {
          break;
        }
        out.write(buffer, 0, read);
      }
      return out.toByteArray();
    } catch (IOException failure) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "File could not be read.");
    }
  }

  private String detectMime(byte[] bytes) {
    String hex = HexFormat.of().formatHex(bytes.length > 16 ? java.util.Arrays.copyOf(bytes, 16) : bytes)
        .toUpperCase(Locale.ROOT);
    String best = null;
    for (Map.Entry<String, String> entry : SIGNATURE_MIME.entrySet()) {
      if (hex.startsWith(entry.getKey())
          && (best == null || entry.getKey().length() > best.length())) {
        best = entry.getKey();
      }
    }
    if (best != null) {
      return SIGNATURE_MIME.get(best);
    }
    boolean text = true;
    for (byte b : bytes) {
      if (b == 0) {
        text = false;
        break;
      }
    }
    return text ? "text/plain" : "application/octet-stream";
  }

  private String resolveMime(String detected, String declared, String extension, String filename) {
    String normalizedDeclared = declared == null ? null : declared.split(";")[0].trim().toLowerCase(Locale.ROOT);
    if (BLOCKED_MIME_EXACT.contains(detected)
        || BLOCKED_MIME_PREFIXES.stream().anyMatch(detected::startsWith)) {
      throw new DomainException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
          ErrorCodes.FILE_TYPE_NOT_ALLOWED,
          "File type is not allowed.");
    }
    if (normalizedDeclared != null
        && (BLOCKED_MIME_EXACT.contains(normalizedDeclared)
            || BLOCKED_MIME_PREFIXES.stream().anyMatch(normalizedDeclared::startsWith)
            || BLOCKED_ACTIVE_MIME.contains(normalizedDeclared))) {
      throw new DomainException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
          ErrorCodes.FILE_TYPE_NOT_ALLOWED,
          "File type is not allowed.");
    }
    if (BLOCKED_ACTIVE_MIME.contains(detected)) {
      throw new DomainException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
          ErrorCodes.FILE_TYPE_NOT_ALLOWED,
          "File type is not allowed.");
    }
    if (!"application/octet-stream".equals(detected)) {
      return detected;
    }
    if (normalizedDeclared != null
        && !normalizedDeclared.isBlank()
        && SAFE_DECLARED_MIME.contains(normalizedDeclared)) {
      return normalizedDeclared;
    }
    return switch (extension) {
      case "png" -> "image/png";
      case "jpg", "jpeg" -> "image/jpeg";
      case "gif" -> "image/gif";
      case "pdf" -> "application/pdf";
      case "txt" -> "text/plain";
      case "zip" -> "application/zip";
      default -> throw new DomainException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
          ErrorCodes.FILE_TYPE_NOT_ALLOWED,
          "File type is not allowed for " + filename + ".");
    };
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 is unavailable.", failure);
    }
  }

  private User requireActive(UUID userId) {
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

  private AttachmentResponse toResponse(Attachment attachment) {
    return new AttachmentResponse(
        attachment.getId(),
        attachment.getMessageId(),
        attachment.getOriginalFilename(),
        attachment.getMimeType(),
        attachment.getSizeBytes(),
        attachment.getSha256(),
        attachment.getCreatedAt());
  }
}
