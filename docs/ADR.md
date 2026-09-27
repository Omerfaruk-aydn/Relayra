# Relayra — Architecture Decision Records

Bu dosya önemli mimari kararların indeksidir.

## ADR-001 — Modular Monolith
**Karar:** İlk sürüm modular monolith.  
**Neden:** Transaction, deployment ve geliştirme basitliği.  
**Sonuç:** Modül sınırları net tutulur; mikroservis sonraya bırakılır.

## ADR-002 — PostgreSQL Source of Truth
**Karar:** Permanent domain data PostgreSQL'dedir.  
**Sonuç:** Redis cache/ephemeral state olarak kullanılır.

## ADR-003 — STOMP over WebSocket
**Karar:** Realtime iletişim Spring WebSocket + STOMP ile başlar.  
**Sonuç:** Subscribe/send authorization ayrıca uygulanır.

## ADR-004 — JWT Access + Rotating Refresh Token
**Karar:** Kısa access token + rotating refresh token.  
**Sonuç:** Refresh token hashlenir, revoke/reuse stratejisi uygulanır.

## ADR-005 — Flyway
**Karar:** Schema migration Flyway ile yönetilir.  
**Sonuç:** Hibernate production'da schema mutate etmez.

## ADR-006 — Cursor Pagination
**Karar:** Mesaj geçmişinde OFFSET kullanılmaz.  
**Sonuç:** `(created_at, id)` tabanlı stabil cursor.

## ADR-007 — Client Message Idempotency
**Karar:** Her client message `clientMessageId` taşır.  
**Sonuç:** Network retry duplicate message üretmez.

## ADR-008 — Soft Delete Messages
**Karar:** Normal message delete soft delete'tir.  
**Sonuç:** Moderation/audit/reply integrity korunabilir.
