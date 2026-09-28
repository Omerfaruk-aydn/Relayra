package com.relayra.attachment.web;

import com.relayra.attachment.AttachmentService;
import com.relayra.attachment.dto.AttachRequest;
import com.relayra.attachment.dto.AttachmentResponse;
import com.relayra.attachment.dto.UploadResponse;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AttachmentController {

  private final AttachmentService attachments;

  public AttachmentController(AttachmentService attachments) {
    this.attachments = attachments;
  }

  @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<UploadResponse> upload(
      @RequestParam(value = "file", required = false) MultipartFile file,
      @RequestParam(value = "channelId", required = false) UUID channelId,
      @RequestParam(value = "conversationId", required = false) UUID conversationId,
      Authentication authentication) {
    UploadResponse uploaded =
        attachments.upload(requireCaller(authentication), channelId, conversationId, file);
    return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
  }

  @PostMapping("/messages/{messageId}/attachments")
  public ResponseEntity<List<AttachmentResponse>> finalizeAttachments(
      @PathVariable UUID messageId,
      @Valid @RequestBody AttachRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        attachments.finalizeAttachments(
            requireCaller(authentication), messageId, request.attachmentIds()));
  }

  @GetMapping("/attachments/{attachmentId}")
  public ResponseEntity<AttachmentResponse> metadata(
      @PathVariable UUID attachmentId, Authentication authentication) {
    return ResponseEntity.ok(
        attachments.metadata(requireCaller(authentication), attachmentId));
  }

  @GetMapping("/attachments/{attachmentId}/download")
  public ResponseEntity<Resource> download(
      @PathVariable UUID attachmentId, Authentication authentication) {
    UUID callerId = requireCaller(authentication);
    var checked = attachments.downloadChecked(callerId, attachmentId);
    return ResponseEntity.ok()
        .contentLength(checked.metadata().sizeBytes())
        .header(HttpHeaders.CONTENT_TYPE, checked.metadata().mimeType())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(checked.metadata().originalFilename())
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(checked.content());
  }

  private UUID requireCaller(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
    return userId;
  }
}
