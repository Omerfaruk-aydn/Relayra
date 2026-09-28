# Relayra — REST API Contract

Base:
```text
/api/v1
```

Content-Type:
```text
application/json
```

---

## 1. Auth

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/auth/me
```

### Login Request
```json
{
  "identifier": "omer@example.com",
  "password": "••••••••"
}
```

### Auth Response
```json
{
  "accessToken": "...",
  "expiresInSeconds": 900,
  "user": {
    "id": "...",
    "username": "omer",
    "displayName": "Ömer"
  }
}
```

Refresh token HttpOnly + Secure + SameSite=Lax cookie ile taşınır.

### Register Request
```json
{
  "username": "omer",
  "email": "omer@example.com",
  "password": "••••••••"
}
```

---

## 2. Users

```http
GET   /api/v1/users/{userId}
GET   /api/v1/users/search?q={query}&limit={n}
PATCH /api/v1/users/me/profile
```

Search sınırlıdır: `q` en az 2, en fazla 64 karakterdir; `limit` varsayılan 20, en fazla 50'dir.

### Profil Güncelleme

Profil güncelleme display name (en fazla 64 karakter), bio (en fazla 500 karakter), timezone ve avatar/banner key alanlarını kabul eder; username ve email değiştirilemez.

---

## 3. Friendship

```http
GET    /api/v1/friends
GET    /api/v1/friends/requests/incoming
GET    /api/v1/friends/requests/outgoing
POST   /api/v1/friends/requests
DELETE /api/v1/friends/requests/{requestId}
POST   /api/v1/friends/requests/{requestId}/accept
POST   /api/v1/friends/requests/{requestId}/reject
DELETE /api/v1/friends/{userId}
POST   /api/v1/users/{userId}/block
DELETE /api/v1/users/{userId}/block
```

Arkadaşlık istekleri `receiverId` içeren body ile oluşturulur; karşılıklı eşzamanlı istek `409 DUPLICATE_RESOURCE` döner.

---

## 4. Communities

```http
POST   /api/v1/communities
GET    /api/v1/communities
GET    /api/v1/communities/{communityId}
PATCH  /api/v1/communities/{communityId}
DELETE /api/v1/communities/{communityId}
POST   /api/v1/communities/{communityId}/leave
```

Community adı 2-100 karakter aralığında olmalıdır. Silme işlemi için body içinde `confirmName` zorunludur.

Ownership transfer gerekirse:
```http
POST /api/v1/communities/{communityId}/transfer-ownership
```

---

## 5. Members / Moderation

```http
GET    /api/v1/communities/{communityId}/members
DELETE /api/v1/communities/{communityId}/members/{userId}
POST   /api/v1/communities/{communityId}/bans
DELETE /api/v1/communities/{communityId}/bans/{userId}
```

Ban isteği `userId` ve opsiyonel `reason` (en fazla 1000 karakter), `expiresAt` alanlarını içerir. Kick `DELETE /api/v1/communities/{communityId}/members/{userId}` (`KICK_MEMBERS`), ban listesi `GET /api/v1/communities/{communityId}/bans` (`BAN_MEMBERS`), audit log `GET /api/v1/communities/{communityId}/audit-log?limit=50&beforeCreatedAt=...&beforeId=...` (`VIEW_AUDIT_LOG`) ile okunur. Banlı kullanıcı community kaynaklarına `403 USER_BANNED` ile erişemez, invite ile katılamaz ve ban sonrası üyeliği sonlandırılır; unban üyeliği otomatik geri getirmez. Rol hiyerarşisi kick/ban'de uygulanır: owner dokunulamaz, eşit veya üst pozisyondaki üye yönetilemez.

---

## 6. Invites

```http
POST   /api/v1/communities/{communityId}/invites
GET    /api/v1/invites/{code}
POST   /api/v1/invites/{code}/join
DELETE /api/v1/invites/{inviteId}
```

---

## 7. Channels

```http
POST   /api/v1/communities/{communityId}/channels
GET    /api/v1/communities/{communityId}/channels
PATCH  /api/v1/channels/{channelId}
DELETE /api/v1/channels/{channelId}
POST   /api/v1/communities/{communityId}/channels/reorder
```

Reorder body sıralı `channelIds` listesi içerir ve transaction içinde uygulanır.

---

## 8. Roles & Permissions

```http
POST   /api/v1/communities/{communityId}/roles
GET    /api/v1/communities/{communityId}/roles
PATCH  /api/v1/roles/{roleId}
DELETE /api/v1/roles/{roleId}
POST   /api/v1/communities/{communityId}/roles/reorder
POST   /api/v1/communities/{communityId}/members/{userId}/roles
GET    /api/v1/communities/{communityId}/members/{userId}/roles
DELETE /api/v1/communities/{communityId}/members/{userId}/roles/{roleId}
GET    /api/v1/communities/{communityId}/permissions/me
```

Managed roller değiştirilemez veya doğrudan atanamaz. Reorder body, sıralı custom `roleIds` listesini eksiksiz içerir. Atama ve kaldırma işlemleri rol hiyerarşisini uygular.

---

## 9. Messages

```http
POST /api/v1/channels/{channelId}/messages
GET  /api/v1/channels/{channelId}/messages?limit=50&beforeCreatedAt=...&beforeId=...
GET  /api/v1/conversations/{conversationId}/messages?limit=50&beforeCreatedAt=...&beforeId=...
```

Mesaj listelerinde `limit` varsayılan 50, en fazla 100'dür; cursor `beforeCreatedAt` + `beforeId` kombinasyonudur.

Edit:
```http
PATCH /api/v1/messages/{messageId}
```

Delete:
```http
DELETE /api/v1/messages/{messageId}
```

Reaction:
```http
PUT    /api/v1/messages/{messageId}/reactions/{emoji}
DELETE /api/v1/messages/{messageId}/reactions/{emoji}
GET    /api/v1/messages/{messageId}/reactions
```

Aynı kullanıcı aynı emojiyi bir kez ekleyebilir; tekrar ekleme ve olmayanı silme idempotent'tir. Emoji NFC ile normalize edilir, en fazla 16 Unicode karakteri kabul edilir ve görünür içerik içermelidir. Silinmiş mesaja reaction `409 MESSAGE_DELETED` döner; mesaj kapsamına erişemeyen kullanıcı `403` (kanal) veya `404` (DM) alır. DM'de block sonrası reaction eklenemez, kaldırılamaz ve yayınlanmaz; geçmiş okuma açıktır. Reaction değişimleri `REACTION_ADDED` / `REACTION_REMOVED` olayı olarak kanal konusuna ve DM katılımcılarının `/user/queue/messages` kuyruğuna yayınlanır; mesaj geçmişi her mesajda `reactions` özetini içerir.

---

## 10. Direct Conversations

```http
POST /api/v1/conversations/direct/{userId}
GET  /api/v1/conversations
GET  /api/v1/conversations/{conversationId}
```

DM gönderimi öncesi block ilişkisi kontrol edilir; block varsa `403 USER_BLOCKED` döner. Aynı iki kullanıcı için tek canonical conversation vardır (ikinci çağrı mevcut olanı döner); kendinle DM `400` döner.

DM mesajları:

```http
POST /api/v1/conversations/{conversationId}/messages
GET  /api/v1/conversations/{conversationId}/messages?limit=50&beforeCreatedAt=...&beforeId=...
```

Realtime DM gönderimi `/app/conversations/{conversationId}/messages` adresine yapılır; ack `/user/queue/acks`, mesaj oluşturma, düzenleme ve silme olayları uygun participant'lara `/user/queue/messages` üzerinden iletilir. Participant olmayan istekler `404` alır. Block sonrası geçmiş okunabilir, ancak yeni mesaj gönderilemez ve mevcut mesajlar düzenlenemez veya silinemez.

---

## 11. Realtime Presence

```http
GET /api/v1/presence
GET /api/v1/presence/status?userIds=...&userIds=...
```

Presence arkadaşlarla sınırlıdır; `/status` arkadaş olmayan ID'leri yanıttan çıkarır ve istek başına en fazla 100 kullanıcı kabul eder. Typing sinyali `/app/channels/{channelId}/typing` üzerinden `{typing:boolean}` payload ile gönderilir ve `/topic/channels/{channelId}/typing` konusuna `TYPING` olayı olarak yayınlanır (`typing:true` 5 sn TTL ile saklanır, `typing:false` durumu hemen temizler ve durdurma sinyalini yayınlar).

---

## 12. Notifications

```http
GET   /api/v1/notifications?limit=50&beforeCreatedAt=...&beforeId=...
PATCH /api/v1/notifications/{notificationId}/read
POST  /api/v1/notifications/read-all
```

Bildirim tipleri `FRIEND_REQUEST`, `MENTION`, `REACTION`, `MODERATION`, `SYSTEM` olur. Mention `@kullaniciadi` sözdizimiyle mesaj içeriğinden çözülür (en fazla 20 farklı kullanıcı). Liste ve okuma işlemleri yalnızca oturum sahibinin bildirimlerine erişir; başkası `404` alır. Bildirim oluşturma hatası ana iş akışını bozmaz (`REQUIRES_NEW` + yutma). Yeni bildirimler `/user/queue/notifications` kuyruğuna `NOTIFICATION_CREATED` olayıyla itilir.

---

## 13. Upload

İki aşamalı model: önce scope ile upload edilir, sonra yazarın kendi mesajına bağlanır.

```http
POST /api/v1/uploads                       # multipart: file + exactly one of channelId|conversationId -> 201
POST /api/v1/messages/{messageId}/attachments  # {"attachmentIds":[...]} (1-10, no duplicates) -> 200
GET  /api/v1/attachments/{attachmentId}         # metadata -> 200
GET  /api/v1/attachments/{attachmentId}/download # bytes (attachment;filename, nosniff) -> 200
```

Kurallar: en fazla 10 MB (beyan + gerçek bayt ikisi de ölçülür), imza tabanlı MIME tespiti (beyan edilen tip yalnızca güvenli allowlist içindeyse kabul edilir), çalıştırılabilir/active içerik engeli (exe/sh/js/html/svg/php ve ELF/Mach-O/class imzaları dahil), dosya adı temizlenir, storage key sunucu üretir. Upload için `ATTACH_FILES` (kanal) veya DM izni, finalize için aynı ek izni + yazarlık + scope eşleşmesi gerekir; mesaj başına en fazla 10 ek. İndirme/metadata korumalıdır: linklenmemiş dosyayı yalnızca yükleyen görür, bağlı dosyada mesaj scope izni aranır. Mesaj soft-delete edildiğinde satırlar korunur, yalnızca baytlar silinir; sonraki indirme/metadata `409 MESSAGE_DELETED` döner.

---

## 14. Error Contract

```json
{
  "timestamp": "2026-09-27T18:00:00Z",
  "status": 403,
  "code": "INSUFFICIENT_PERMISSION",
  "message": "You do not have permission to perform this action.",
  "path": "/api/v1/...",
  "requestId": "01H..."
}
```

Validation:
```json
{
  "timestamp": "...",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "path": "/api/v1/...",
  "requestId": "...",
  "errors": {
    "username": "Username must contain between 3 and 32 characters."
  }
}
```

---

## 15. Error Codes

Minimum:
- VALIDATION_FAILED
- AUTHENTICATION_REQUIRED
- INVALID_CREDENTIALS
- TOKEN_EXPIRED
- TOKEN_INVALID
- RESOURCE_NOT_FOUND
- ACCESS_DENIED
- INSUFFICIENT_PERMISSION
- CONFLICT
- DUPLICATE_RESOURCE
- USER_BLOCKED
- USER_BANNED
- INVITE_INVALID
- INVITE_EXPIRED
- INVITE_EXHAUSTED
- MESSAGE_DELETED
- RATE_LIMITED
- FILE_TOO_LARGE
- FILE_TYPE_NOT_ALLOWED
- INTERNAL_ERROR

---

## 16. HTTP Status Semantics

- 200 successful read/update
- 201 created
- 204 successful delete/no body
- 400 malformed/validation
- 401 unauthenticated
- 403 authenticated but forbidden
- 404 not found OR security-motivated concealment where appropriate
- 409 conflict
- 413 payload too large
- 415 unsupported media
- 422 optional for semantic validation if project standardizes it
- 429 rate limited
- 500 unexpected error

---

## 17. Idempotency

Mesaj gönderimi WebSocket tarafında `clientMessageId` kullanır.

REST create endpointlerinde kritik duplicate risk varsa `Idempotency-Key` standardı değerlendirilebilir.

---

## 18. API Security

Her endpoint için:
- authenticated?
- resource owner?
- community member?
- permission?
- block/ban state?
- rate limit?
- input bounds?

kontrol listesi uygulanmalıdır.
