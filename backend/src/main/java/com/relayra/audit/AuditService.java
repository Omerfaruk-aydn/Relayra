package com.relayra.audit;

import com.relayra.audit.domain.AuditEntry;
import com.relayra.audit.dto.AuditEntryResponse;
import com.relayra.audit.dto.AuditPageResponse;
import com.relayra.audit.persistence.AuditEntryRepository;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

  private final AuditEntryRepository entries;

  public AuditService(AuditEntryRepository entries) {
    this.entries = entries;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      UUID communityId, UUID actorId, String action, UUID targetUserId, UUID targetId, String detail) {
    AuditEntry entry = new AuditEntry(UUID.randomUUID(), communityId, actorId, action);
    entry.describe(targetUserId, targetId, detail == null ? null : detail.trim().isEmpty() ? null : detail.trim());
    entries.save(entry);
  }

  @Transactional(readOnly = true)
  public AuditPageResponse history(
      UUID communityId, Integer limit, Instant beforeCreatedAt, UUID beforeId) {
    validateCursor(beforeCreatedAt, beforeId);
    List<AuditEntry> page =
        entries.findCommunityHistory(communityId, beforeCreatedAt, beforeId, pageRequest(limit));
    List<AuditEntryResponse> responses =
        page.stream()
            .map(
                entry ->
                    new AuditEntryResponse(
                        entry.getId(),
                        entry.getCommunityId(),
                        entry.getActorId(),
                        entry.getAction(),
                        entry.getTargetUserId(),
                        entry.getTargetId(),
                        entry.getDetail(),
                        entry.getCreatedAt()))
            .toList();
    AuditEntry last = page.isEmpty() ? null : page.get(page.size() - 1);
    return new AuditPageResponse(
        responses, last == null ? null : last.getCreatedAt(), last == null ? null : last.getId());
  }

  private PageRequest pageRequest(Integer limit) {
    int size = limit == null ? 50 : limit;
    if (size < 1 || size > 100) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "limit must be between 1 and 100.");
    }
    return PageRequest.of(0, size);
  }

  private void validateCursor(Instant beforeCreatedAt, UUID beforeId) {
    if ((beforeCreatedAt == null) != (beforeId == null)) {
      throw new DomainException(
          HttpStatus.BAD_REQUEST.value(),
          ErrorCodes.VALIDATION_FAILED,
          "beforeCreatedAt and beforeId must be provided together.");
    }
  }
}
