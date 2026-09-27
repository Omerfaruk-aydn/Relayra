# Relayra — WebSocket & Realtime Protocol

## 1. Protocol

Transport:
- WebSocket / WSS

Application protocol:
- STOMP over WebSocket

Endpoint:
```text
/ws
```

---

## 2. Connection Lifecycle

```text
DISCONNECTED
  ↓
CONNECTING
  ↓
AUTHENTICATING
  ↓
CONNECTED
  ↓
RECONNECTING
  ↓
CONNECTED
```

Fatal auth failure:
```text
AUTH_FAILED
```

---

## 3. Authentication

CONNECT frame sırasında JWT doğrulanır.

Kontroller:
- signature,
- issuer/audience if used,
- expiration,
- account status,
- token version/revocation strategy if applicable.

Invalid auth ile subscription kabul edilmez.

---

## 4. Authorization

Subscribe authorization da yapılmalıdır.

Sadece send route'u kontrol etmek yeterli değildir.

Örnek:
```text
/topic/channels/{channelId}/messages
```

subscribe ederken user'ın channel access'i doğrulanmalıdır.

---

## 5. Message Send

Destination:
```text
/app/channels/{channelId}/messages
```

Payload:
```json
{
  "clientMessageId": "01J...",
  "content": "Merhaba",
  "replyToMessageId": null
}
```

Server flow:
1. authenticate
2. validate payload
3. authorize scope
4. rate limit
5. idempotency
6. persist
7. commit
8. broadcast

---

## 6. Message Event

```json
{
  "eventId": "01J...",
  "type": "MESSAGE_CREATED",
  "occurredAt": "2026-09-27T18:05:00Z",
  "data": {
    "id": "...",
    "clientMessageId": "...",
    "channelId": "...",
    "author": {
      "id": "...",
      "username": "omer",
      "displayName": "Ömer"
    },
    "content": "Merhaba",
    "replyTo": null,
    "createdAt": "..."
  }
}
```

---

## 7. Edit / Delete Events

```text
MESSAGE_UPDATED
MESSAGE_DELETED
```

Eventler canonical server state içermelidir.

---

## 8. Typing

Send:
```text
/app/channels/{channelId}/typing
```

Broadcast:
```text
/topic/channels/{channelId}/typing
```

Payload:
```json
{
  "typing": true
}
```

Server userId'yi authenticated principal'dan almalıdır.
Client'ın gönderdiği userId'ye güvenilmemelidir.

Typing:
- ephemeral,
- DB'ye yazılmaz,
- TTL 5 saniye,
- throttled.

---

## 9. Presence

Presence event:
```json
{
  "eventId": "...",
  "type": "PRESENCE_CHANGED",
  "data": {
    "userId": "...",
    "status": "ONLINE",
    "changedAt": "..."
  }
}
```

Multi-device kuralı:
- user'ın en az bir active connection'ı varsa OFFLINE olmamalıdır.
- Son connection kapandıktan sonra 30 saniye grace period uygulanır.

---

## 10. DM

Send:
```text
/app/conversations/{conversationId}/messages
```

Private receive:
```text
/user/queue/messages
```

Server participant check zorunlu.

---

## 11. Error Event

```json
{
  "type": "ERROR",
  "requestId": "...",
  "code": "INSUFFICIENT_PERMISSION",
  "message": "You cannot send messages to this channel."
}
```

Internal exception detail client'a gönderilmez.

---

## 12. Reconnect

Capped exponential backoff:
```text
1s
2s
4s
8s
16s
30s
```

Jitter eklenmesi önerilir.

Reconnect sonrası:
- token refresh gerekirse yapılır,
- subscriptions restore edilir,
- REST sync yapılır,
- duplicate local optimistic messages reconcile edilir.

---

## 13. Delivery Semantics

Exactly-once guarantee iddia edilmez.

Network retry duplicate üretebilir.

Koruma:
- clientMessageId,
- unique constraint,
- canonical server acknowledgement.

---

## 14. Ordering

Mesajlar:
- server timestamp,
- stable secondary key olarak message ID

ile sıralanır.

Aynı timestamp oluşması pagination'ı bozmamalıdır.

---

## 15. Backpressure / Abuse

- message send rate limit (30/dakika/kullanıcı)
- typing throttle (60/dakika/kullanıcı)
- max frame/payload size 64 KB
- max message length 4000 karakter
- max subscription count if necessary
- malformed frame handling

tanımlanmalıdır.
