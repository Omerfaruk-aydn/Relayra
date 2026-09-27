# Relayra — Application Specification

## 1. Ürün Özeti

**Relayra**, kullanıcıların birebir ve topluluk tabanlı olarak gerçek zamanlı iletişim kurabildiği modern bir web uygulamasıdır.

Uygulama aşağıdaki temel kullanım biçimlerini destekler:

- kullanıcı hesabı ve profil yönetimi,
- kullanıcı arama ve arkadaşlık,
- direct message,
- topluluk oluşturma ve yönetme,
- topluluk içi text channel'ları,
- gerçek zamanlı mesajlaşma,
- mesaj düzenleme/silme/reply/reaction,
- online presence,
- typing indicator,
- unread state,
- bildirimler,
- rol ve izin sistemi,
- moderasyon,
- güvenli dosya paylaşımı,
- audit log.

Relayra, Discord/Slack benzeri ürün kategorisindedir ancak birebir ürün veya tasarım kopyası olmayacaktır.

---

## 2. Ürün Hedefleri

### Birincil Hedefler

1. Java 21 ve Spring Boot ile ciddi backend pratiği sağlamak.
2. Realtime messaging ve WebSocket lifecycle'ını gerçek bir ürün üzerinde öğretmek.
3. Authentication ve authorization'ı production mantığına yakın uygulamak.
4. PostgreSQL üzerinde doğru veri modelleme, constraint ve indexing pratiği sağlamak.
5. Redis'i sadece ihtiyaç olan alanlarda kullanmak.
6. Güvenli, test edilebilir ve sürdürülebilir bir kod tabanı oluşturmak.
7. React + TypeScript ile modern ve profesyonel bir web istemcisi geliştirmek.

### İkincil Hedefler

- Docker tabanlı local development.
- İleri aşamada horizontal scaling'e hazırlanmak.
- Observability ve structured logging pratiği.
- Gerçek sistem edge-case'lerini çözmek.

---

## 3. Hedef Kullanıcı Akışları

### 3.1 İlk Kayıt

1. Kullanıcı register sayfasını açar.
2. Username, email ve password girer.
3. Client-side validation çalışır.
4. Backend aynı validation'ı tekrarlar.
5. Username/email uniqueness database tarafından da garanti edilir.
6. Password hashlenir.
7. User + Profile transaction içinde oluşturulur.
8. Kullanıcı login olabilir.

### 3.2 Login

1. Email/username + password gönderilir.
2. Rate limit kontrol edilir.
3. Credential doğrulanır.
4. Access token üretilir.
5. Refresh token üretilir.
6. Refresh token hashlenerek saklanır.
7. Security event loglanır.
8. Client authenticated state'e geçer.

### 3.3 Arkadaşlık

1. Kullanıcı başka kullanıcıyı arar.
2. Kendine istek atamaz.
3. Block ilişkisi varsa istek gönderilemez.
4. Duplicate pending request oluşturulamaz.
5. Karşılıklı eşzamanlı istek özel kuralla normalize edilir.
6. Kabul edildiğinde ilişki atomik olarak ACCEPTED olur.
7. İlgili notification üretilir.

### 3.4 Community Oluşturma

1. Kullanıcı community adı girer.
2. Community oluşturulur.
3. Owner membership aynı transaction içinde oluşturulur.
4. Default role oluşturulur.
5. Default `general` channel oluşturulabilir.
6. Owner gerekli tüm yönetim yetkilerine sahip olur.

### 3.5 Davet ile Katılma

1. Invite code resolve edilir.
2. Revoke edilmiş mi kontrol edilir.
3. Expired mı kontrol edilir.
4. Usage limit dolmuş mu kontrol edilir.
5. User banned mı kontrol edilir.
6. User zaten üye mi kontrol edilir.
7. Üyelik ve usage_count transaction içinde güncellenir.
8. Race condition'a karşı locking/atomic strategy kullanılır.

### 3.6 Channel Mesajlaşma

1. WebSocket message event gelir.
2. Session authenticated mı kontrol edilir.
3. Payload schema doğrulanır.
4. Community membership kontrol edilir.
5. Channel read/send permission kontrol edilir.
6. Rate limit kontrol edilir.
7. `clientMessageId` ile idempotency kontrol edilir.
8. Message database'e kaydedilir.
9. Transaction başarıyla commit edilir.
10. Realtime broadcast yapılır.
11. Mention/notification eventleri işlenir.

### 3.7 Direct Message

DM göndermeden önce:
- conversation participant mı,
- karşı taraf block etmiş mi,
- sender blocked mı,
- rate limit aşılmış mı

kontrol edilir.

### 3.8 Mesaj Düzenleme

- Yalnızca mesaj sahibi içerik düzenleyebilir.
- Moderator başka kullanıcının mesaj içeriğini değiştiremez.
- Soft-deleted mesaj düzenlenemez.
- System message düzenlenemez.
- Edit sonrası `edited_at` set edilir.
- Edit event broadcast edilir.

### 3.9 Mesaj Silme

- Mesaj sahibi kendi mesajını soft delete edebilir.
- Moderator gerekli permission ile başkasının mesajını soft delete edebilir.
- Hard delete normal kullanıcı aksiyonu değildir.
- Silinen mesajın attachment erişimi kurala göre kapatılır.
- Audit gerektiğinde tutulur.

---

## 4. Kullanıcı Rolleri

### Global
- Anonymous
- Authenticated User

### Community Level
- Owner
- Admin
- Moderator
- Member

Roller isimden bağımsız permission setleriyle yönetilir.

Owner özel bir sistem statüsüdür ve community ownership transfer edilmeden kaldırılamaz.

---

## 5. Permission Listesi

Minimum permission set:

- VIEW_CHANNEL
- SEND_MESSAGES
- ADD_REACTIONS
- ATTACH_FILES
- CREATE_INVITES
- MANAGE_INVITES
- MANAGE_CHANNELS
- MANAGE_ROLES
- MANAGE_MEMBERS
- KICK_MEMBERS
- BAN_MEMBERS
- DELETE_MESSAGES
- MANAGE_COMMUNITY
- VIEW_AUDIT_LOG

### Permission Resolution

Effective permission:

```text
base member permissions
+ assigned role permissions
+ owner override
- explicit system restrictions
```

İlk sürümde channel-specific permission override yoktur.

---

## 6. Mesaj Davranışı

### Mesaj Türleri
- TEXT
- SYSTEM
- IMAGE
- FILE

### Durumlar
- sending (client-only)
- sent
- edited
- deleted
- failed (client-only)

### Kurallar

- Boş mesaj kabul edilmez.
- Sadece whitespace mesaj kabul edilmez.
- Maksimum içerik uzunluğu sabit ve documented olmalıdır.
- HTML backend'de trusted content olarak yorumlanmamalıdır.
- Frontend user-generated content'i escape ederek render etmelidir.
- `reply_to_message_id` aynı conversation/channel scope içinde olmalıdır.
- Silinmiş mesaja yeni reaction eklenemez.
- Message edit, `clientMessageId` oluşturma semantiğini değiştirmez.

---

## 7. Realtime Beklentileri

### Delivery Semantics

Amaç "exactly once" iddiası değildir.

Uygulama:
- at-least-once network behavior'ı tolere eder,
- duplicate'i `clientMessageId` ile engeller,
- server-generated message ID ile canonical state sağlar.

### Ordering

- Client local clock sıralama kaynağı değildir.
- Server-side `created_at` canonical timestamp'tir.
- Aynı channel içinde stabil pagination gerekir.
- Cursor, sadece timestamp yerine `(created_at, id)` kombinasyonuna dayanabilir.

### Reconnect

Reconnect olduğunda:
1. WebSocket tekrar authenticate edilir.
2. Subscription'lar restore edilir.
3. Son bilinen cursor üzerinden missed data çekilir.
4. Client cache server state ile reconcile edilir.

---

## 8. Non-Functional Requirements

### Güvenlik
- Default deny authorization.
- No plaintext secret storage.
- No token logging.
- IDOR testleri zorunlu.
- Upload security zorunlu.
- Rate limit kritik endpointlerde zorunlu.

### Performans
- Mesaj listesi cursor pagination kullanır.
- UI uzun listelerde virtualization kullanabilir.
- Sınırsız query yoktur.
- Büyük response'lar bounded olmalıdır.

### Reliability
- Kritik multi-write işlemler transaction kullanır.
- Retry-safe işlemler için idempotency düşünülür.
- Health check vardır.
- Migration deterministic olmalıdır.

### Maintainability
- Controller ince tutulur.
- Business logic service/domain katmanında tutulur.
- DTO/entity ayrımı korunur.
- Domain invariant'lar test edilir.

---

## 9. MVP Definition of Done

MVP tamamlanmış sayılması için:

- Auth eksiksiz,
- Friends eksiksiz,
- Community eksiksiz,
- Channel CRUD,
- Channel messaging realtime,
- DM realtime,
- presence,
- typing,
- reactions,
- replies,
- edit/delete,
- unread tracking,
- role/permission,
- moderation,
- notifications,
- attachment upload,
- Docker local setup,
- automated test suite,
- security test suite,
- docs,
- health endpoint

çalışmalıdır.

İki farklı tarayıcı oturumunda realtime akış kabul testi geçmelidir.

---

## 10. Non-Goals — İlk Sürümde Yok

- voice chat,
- video chat,
- screen sharing,
- full-text message search,
- threads,
- bots,
- webhook platform,
- custom emoji marketplace,
- billing,
- public community discovery,
- OAuth login,
- 2FA.

Bunlar MVP'yi geciktirmemelidir.
