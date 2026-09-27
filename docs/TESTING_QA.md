# Relayra — Testing & QA Strategy

## 1. Hedef

Testlerin amacı yalnızca coverage yüzdesi değildir.

Amaç:
- domain invariant'ları korumak,
- security regression engellemek,
- concurrency hatalarını yakalamak,
- API contract'ı sabitlemek,
- realtime davranışı doğrulamaktır.

---

## 2. Test Piramidi

### Unit
Hızlı, isolated:
- domain rules
- permission resolution
- validator
- mapper
- service branch logic

### Integration
Gerçek altyapıya yakın:
- PostgreSQL Testcontainers
- Redis Testcontainers gerekirse
- repository
- transaction
- migration
- Spring Security
- REST

### WebSocket Integration
- connect
- auth
- subscribe
- send
- receive
- forbidden subscribe
- reconnect-adjacent behavior

### E2E
Kritik user journey:
- register -> login
- create community
- invite user
- join
- send message
- receive realtime
- reaction
- edit/delete
- DM

---

## 3. Mandatory Test Classes

- AuthServiceTest
- RefreshTokenServiceTest
- FriendshipServiceTest
- CommunityServiceTest
- InviteServiceTest
- PermissionServiceTest
- ChannelServiceTest
- MessageServiceTest
- ReactionServiceTest
- ModerationServiceTest
- AttachmentServiceTest

Integration:
- AuthIntegrationTest
- FriendshipIntegrationTest
- CommunityIntegrationTest
- MessagingIntegrationTest
- AuthorizationIntegrationTest
- WebSocketIntegrationTest

---

## 4. Security Negative Tests

Zorunlu:
- unauthenticated protected endpoint
- invalid JWT
- expired JWT
- tampered JWT
- revoked refresh token
- refresh reuse
- edit someone else's message
- delete without permission
- read private conversation as outsider
- subscribe unauthorized channel
- banned join
- blocked DM
- assign higher role without authority
- remove owner
- retrieve someone else's notifications
- unauthorized attachment download

---

## 5. Concurrency Tests

Önemli:
- two users consume last invite use concurrently
- duplicate reaction concurrently
- duplicate message retry
- same username concurrent register
- role reorder concurrent update
- duplicate direct conversation create

Test sonucunda database invariant bozulmamalıdır.

---

## 6. Migration Tests

CI'da:
- empty DB -> latest migration
- existing representative schema -> latest migration
- Hibernate validate passes

---

## 7. API Contract Tests

Doğrulanır:
- status code
- response shape
- validation error shape
- error code
- pagination cursor behavior
- no sensitive field leakage

---

## 8. WebSocket Tests

Test:
- valid connect
- invalid connect
- authorized subscribe
- unauthorized subscribe
- valid send
- duplicate clientMessageId
- typing throttle
- message broadcast
- edit/delete broadcast
- private DM delivery

---

## 9. Frontend Tests

Minimum:
- form validation
- auth state
- protected routes
- optimistic message rendering
- failed send retry
- reconnect banner
- permission-gated controls
- empty/loading/error states

---

## 10. Manual QA Checklist

Her release öncesi:
- fresh account
- two-account test
- two-browser realtime
- slow network
- offline/reconnect
- expired token
- refresh flow
- blocked user
- banned member
- large message
- invalid upload
- duplicate click
- repeated send
- tab refresh
- browser back/forward

---

## 11. Coverage

Coverage hedefi kör şekilde %100 değildir.

Kritik domain/security service'lerde yüksek branch coverage beklenir.

Coverage düşükse neden bilinmelidir.

---

## 12. CI Quality Gates

Fail:
- test failure
- migration failure
- formatting/lint failure
- compilation failure
- critical vulnerability
- contract regression
