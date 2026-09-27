# Relayra — System Architecture

## 1. Mimari Stil

Relayra bir **Modular Monolith** olarak başlayacaktır.

Amaç:
- basit deployment,
- güçlü transaction sınırları,
- düşük operasyonel yük,
- net modül sınırları,
- gerektiğinde servis extraction.

---

## 2. High-Level

```text
Browser
  |
  | HTTPS / WSS
  v
React + TypeScript
  |
  +---- REST ------------------------------+
  |                                        |
  +---- STOMP/WebSocket ----------------+  |
                                       |  |
                             Spring Boot Backend
                                       |
              +------------------------+----------------------+
              |                        |                      |
         PostgreSQL                  Redis                Object Storage
         durable state          ephemeral state          attachments
```

---

## 3. Layering

Bir modül tipik olarak:

```text
web/controller
application/service
domain
persistence/repository
dto
mapper
event
```

katmanlarına ayrılabilir.

Amaç katman sayısını artırmak değil; bağımlılıkları anlaşılır kılmaktır.

---

## 4. Dependency Rules

- `controller -> application/service`
- `service -> repository/domain`
- `repository -> database`
- domain mümkün olduğunca framework bağımlılığından uzak tutulabilir.
- controller başka modül repository'sine doğrudan gitmez.
- circular dependency yoktur.

---

## 5. Transaction Boundaries

Transaction gerektiren örnekler:
- user + profile create
- community + owner membership + defaults create
- friend request accept
- invite join + usage increment
- role assignment
- message create
- ban + membership revoke

`@Transactional` rastgele her metoda konulmaz.

Transaction:
- mümkün olduğunca kısa,
- network call içermeyen,
- database consistency sağlayan

boundary olmalıdır.

---

## 6. Domain Event Strategy

Transaction sonrası yayınlanması gereken eventler için "commit olmadan yayınlama" problemi dikkate alınmalıdır.

Öneri:
- application event + after-commit listener,
- veya daha ileri aşamada outbox pattern.

MVP'de networked message broker zorunlu değildir.

---

## 7. Message Flow

```text
WebSocket inbound
  ↓
Authentication
  ↓
Schema validation
  ↓
Scope authorization
  ↓
Rate limit
  ↓
Idempotency check
  ↓
MessageService
  ↓
PostgreSQL commit
  ↓
after-commit event
  ↓
broadcast / notification / mention
```

---

## 8. Cache Strategy

Redis source of truth değildir.

Uygun kullanım:
- presence
- typing TTL
- rate limit counters
- short-lived cache
- multi-instance realtime fanout

Cache invalidation gerektiren permanent domain data MVP'de gereksiz yere Redis'e taşınmamalıdır.

---

## 9. Horizontal Scale Hazırlığı

Tek instance başlangıç.

Daha sonra:
- load balancer,
- multiple backend instances,
- Redis Pub/Sub,
- shared PostgreSQL,
- shared object storage

kullanılabilir.

Session-local realtime state buna göre tasarlanmalıdır.

---

## 10. Failure Boundaries

### PostgreSQL down
- write işlemleri fail fast,
- meaningful error,
- health degraded/down.

### Redis down
- kritik olmayan presence/typing degrade olabilir.
- auth ve permanent message data PostgreSQL'e bağlı kalır.
- sistem Redis unavailable davranışını açıkça tanımlar.

### Object Storage down
- attachment upload fail olur,
- text messaging çalışmaya devam edebilir.

---

## 11. Time

- Backend/database UTC.
- API ISO-8601.
- Frontend lokal timezone formatlar.
- Client clock authoritative değildir.

---

## 12. Configuration

Config priority:
1. env vars
2. application profile defaults
3. no hardcoded secrets

Profiles:
- local
- test
- production

---

## 13. Error Handling

Tüm REST hataları ortak `ApiError` contract'ı kullanır.

WebSocket hataları için:
- protocol-level error event,
- correlation/request ID,
- generic safe message,
- internal stack trace sadece loglarda

kullanılır.
