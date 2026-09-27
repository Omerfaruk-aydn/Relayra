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

Refresh token tercihen HttpOnly Secure cookie modelinde değerlendirilebilir.
Eğer body ile taşınırsa threat model ve storage kararı açıkça documented olmalıdır.

---

## 2. Users

```http
GET   /api/v1/users/{userId}
GET   /api/v1/users/search?q={query}&limit={n}
PATCH /api/v1/users/me/profile
```

Search bounded olmalıdır.

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

---

## 8. Messages

```http
GET /api/v1/channels/{channelId}/messages?limit=50&beforeCreatedAt=...&beforeId=...
GET /api/v1/conversations/{conversationId}/messages?limit=50&beforeCreatedAt=...&beforeId=...
```

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
```

---

## 9. Direct Conversations

```http
POST /api/v1/conversations/direct/{userId}
GET  /api/v1/conversations
GET  /api/v1/conversations/{conversationId}
```

---

## 10. Notifications

```http
GET   /api/v1/notifications?limit=50&cursor=...
PATCH /api/v1/notifications/{notificationId}/read
POST  /api/v1/notifications/read-all
```

---

## 11. Upload

Tercih edilen iki aşamalı model:

```http
POST /api/v1/uploads
```

Backend:
- metadata validate eder,
- güvenli upload target döner veya stream kabul eder.

Attachment finalize:
```http
POST /api/v1/messages/{messageId}/attachments
```

---

## 12. Error Contract

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

## 13. Error Codes

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

## 14. HTTP Status Semantics

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

## 15. Idempotency

Mesaj gönderimi WebSocket tarafında `clientMessageId` kullanır.

REST create endpointlerinde kritik duplicate risk varsa `Idempotency-Key` standardı değerlendirilebilir.

---

## 16. API Security

Her endpoint için:
- authenticated?
- resource owner?
- community member?
- permission?
- block/ban state?
- rate limit?
- input bounds?

kontrol listesi uygulanmalıdır.
