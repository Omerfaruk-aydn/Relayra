# Relayra — Domain Rules & Invariants

Bu dosya sistemin "değişmemesi gereken gerçeklerini" tanımlar.

Kod, API ve database tasarımı bu kuralları ihlal etmemelidir.

---

## 1. User

- Username global olarak unique olmalıdır.
- Email global olarak unique olmalıdır.
- Username normalize stratejisi tek olmalıdır.
- Email compare case-insensitive olmalıdır.
- Password hiçbir zaman plaintext saklanmaz.
- Disabled/banned global account varsa authentication ve realtime erişim politikası açıkça uygulanır.

---

## 2. Friendship

- User kendine friend request gönderemez.
- Aynı iki kullanıcı arasında birden fazla aktif pending ilişki bulunamaz.
- Block, friend request ve DM'i engeller.
- Bir kullanıcı diğerini block ederse aradaki ACCEPTED friendship sonlandırılır, bekleyen istekler iptal edilir.
- Accept yalnızca request receiver tarafından yapılabilir.
- Reject yalnızca receiver tarafından yapılabilir.
- Request sender kendi gönderdiği pending isteği cancel edebilir.
- Duplicate concurrent friend request database/transaction seviyesiyle kontrol edilmelidir.

---

## 3. Community

- Her community'nin tek bir owner'ı vardır.
- Owner community member olmak zorundadır.
- Owner membership yanlışlıkla silinemez.
- Owner community'den ayrılamaz; önce ownership transfer veya community delete gerekir.
- Community silme işlemi authorization + explicit confirmation gerektirir.
- Community silinince memberships, channels, roles, invites ve bans cascade silinir; audit log satırları `community_id` NULL yapılarak korunur.

---

## 4. Membership

- Bir user aynı community'ye bir kez üye olabilir.
- Banned user invite ile katılamaz.
- Membership silinince role assignment'lar temizlenmelidir.
- Membership olmadan community channel mesajları okunamaz.
- Community owner membership status normal member gibi disable edilemez.

---

## 5. Invite

- Invite code kriptografik rastgele üretilir, URL-safe ve en az 8 karakterdir.
- Invite code unique olmalıdır.
- Revoked invite kullanılamaz.
- Expired invite kullanılamaz.
- maxUses dolmuş invite kullanılamaz.
- usageCount hiçbir zaman maxUses'ı aşmamalıdır.
- Join ve usage increment atomik olmalıdır.
- Aynı user zaten üyeyse invite usage artırılmamalıdır.
- Banned user usage artırmadan reddedilmelidir.

---

## 6. Channel

- Channel bir community'ye aittir.
- Channel adı trim edilir, uzunluğu 2-100 karakter aralığında olmalıdır.
- Silinmiş channel'a mesaj gönderilemez.
- Channel'a erişim yalnızca community membership + permission ile mümkündür.
- Position reorder işlemi transaction içinde tutarlı sonuç üretmelidir.

---

## 7. Message

- Message ya channel'a ya conversation'a aittir; ikisine birden ait olamaz.
- Message hiçbir scope'a ait olmadan oluşturulamaz.
- Author ilgili scope'a erişim hakkına sahip olmalıdır.
- `clientMessageId` sender scope'unda duplicate engellemek için kullanılmalıdır.
- Reply target aynı scope içinde olmalıdır.
- Kullanıcı sadece kendi mesaj içeriğini edit edebilir.
- Moderator başkasının mesajını delete edebilir ama edit edemez.
- Soft deleted message edit edilemez.
- Deleted message yeni reaction kabul etmez.
- Message content limitleri server-side uygulanır.
- Content trusted HTML değildir.

---

## 8. Reaction

- Aynı user aynı message'a aynı emoji reaction'ı bir kez ekleyebilir.
- Add/remove idempotent davranış gösterebilir.
- Deleted message'a reaction eklenemez.
- User message scope'una erişemiyorsa reaction ekleyemez.

---

## 9. Conversation / DM

- User conversation participant değilse history okuyamaz.
- User conversation participant değilse message gönderemez.
- Block ilişkisi DM send'i engeller.
- Aynı iki kullanıcı için tek canonical direct conversation vardır.
- `direct_pair_key`, iki user ID sıralanarak `min:max` formatında üretilir.

---

## 10. Presence

- Presence persistent source of truth değildir.
- Redis gibi ephemeral store kullanılabilir.
- WebSocket disconnect sonrası 30 saniye grace period uygulanır.
- Bir user'ın birden fazla active connection'ı olabilir.
- Tek connection kapanınca diğer connection açıksa user OFFLINE olmamalıdır.

---

## 11. Typing

- Typing event DB'ye yazılmaz.
- TTL 5 saniyedir.
- Spam önlemek için client debounce + server rate limit uygulanabilir.
- User scope'a erişemiyorsa typing event yayınlayamaz.

---

## 12. Roles & Permissions

- Permission kontrolü server-side yapılır.
- Bir user'ın kendi sahip olmadığı rolü yükseltme riski engellenmelidir.
- Role hierarchy/position kuralları tanımlanmalıdır.
- Admin kendinden yüksek role sahip member'ı yönetememelidir.
- Owner tüm community permissions'a sahiptir.
- Owner role silme/atama semantiği normal role gibi ele alınmamalıdır.
- Default member rolü `VIEW_CHANNEL`, `SEND_MESSAGES`, `ADD_REACTIONS` ve `ATTACH_FILES` izinlerine sahiptir.
- İlk sürümde channel-specific permission override yoktur.

---

## 13. Ban

- Banned user community'ye join olamaz.
- Banned user community WebSocket topics'lerine subscribe olamaz.
- Ban sırasında active membership sonlandırılır.
- Temporary ban expiration kontrolü centralized olmalıdır.
- Unban otomatik olarak membership oluşturmaz.

---

## 14. Attachment

- Attachment message scope'una bağlıdır.
- Unauthorized user attachment URL alamaz.
- Original filename storage key olarak kullanılmaz.
- MIME, size ve extension doğrulanır.
- Executable upload kısıtlanır.
- Deleted message attachment erişim politikası açık olmalıdır.

---

## 15. Notification

- Notification delivery başarısızlığı ana business transaction'ı gereksiz yere bozmayabilir.
- Notification oluşturma idempotency gerekirse event ID ile sağlanır.
- Kullanıcı sadece kendi notification'larını görebilir.
- `type` değeri `FRIEND_REQUEST`, `MENTION`, `REACTION`, `MODERATION` veya `SYSTEM` olur.

---

## 16. Audit

Audit edilmesi gereken minimum aksiyonlar:
- community update/delete
- channel create/update/delete
- role create/update/delete
- role assignment
- invite create/revoke
- kick
- ban/unban
- moderator message delete

Audit log normal kullanıcı tarafından değiştirilemez.
