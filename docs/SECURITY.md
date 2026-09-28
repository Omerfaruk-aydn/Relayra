# Relayra — Security Specification

## 1. Güvenlik Yaklaşımı

Varsayılan:
```text
deny by default
```

Güvenlik sadece Spring Security config değildir.

Her resource access domain authorization ister.

---

## 2. Password

Tercih:
- Argon2id
veya
- güncel cost ile BCrypt

Kurallar:
- plaintext yok,
- log yok,
- response yok,
- reversible encryption yok.

Password policy:
- minimum 8 karakter,
- maksimum 72 karakter (bcrypt sınırı, DoS koruması),
- aşırı karmaşık zorunluluk yerine güçlü uzunluk,
- common-password kontrolü opsiyonel.

---

## 3. Access Token

JWT:
- kısa ömürlü (access token ömrü 15 dakika),
- güçlü imza,
- `sub`, `iat`, `exp`, `jti`,
- issuer/audience uygun ise kullanılmalı.

Token'a uzun ömürlü dynamic permission set gömülmemelidir.

---

## 4. Refresh Token

- random high-entropy token,
- hash stored,
- rotation,
- family tracking,
- revoke,
- yeniden kullanım tespitinde tüm token family revoke edilir.

Token taşıma modeli kilitlidir: access token `Authorization: Bearer` header ile taşınır, refresh token HttpOnly + Secure + SameSite=Lax cookie ile taşınır.

Refresh token ömrü 30 gündür; her kullanımda rotation yapılır.

CSRF etkisi seçilen token taşıma modeline göre ayrıca ele alınmalıdır.

---

## 5. Authentication Abuse

Login/register:
- rate limit,
- generic invalid credential message,
- timing leakage minimize,
- brute-force monitoring,
- suspicious activity logging.

Account enumeration minimize edilmelidir.

---

## 6. Authorization

Kontrol seviyeleri:
1. authenticated mı?
2. account usable mı?
3. resource var mı?
4. user resource scope'una erişebilir mi?
5. gerekli permission var mı?
6. block/ban/ownership state uygun mu?

IDOR negatif testleri zorunludur.

---

## 7. WebSocket Security

- CONNECT auth
- SUBSCRIBE auth
- SEND auth
- payload validation
- frame size limit
- message rate limit
- destination whitelist
- client-supplied identity fields'e güvenmeme

---

## 8. XSS

Message content plain user-generated text kabul edilmelidir.

Frontend:
- raw HTML render etmemeli,
- gerekiyorsa markdown sanitizer kullanılmalı,
- URL preview gibi özellikler eklenirse SSRF/XSS threat model oluşturulmalı.

---

## 9. CSRF

Auth token cookie tabanlı ise CSRF koruması yeniden değerlendirilmelidir.

Bearer token header modelinde risk profili farklıdır.

Seçilen auth model `SECURITY.md` içinde implementation sırasında kesinleştirilmelidir.

---

## 10. CORS

Production:
- wildcard origin kullanılmamalı,
- sadece trusted frontend origins.

Credentials kullanılıyorsa `*` yasaktır.

---

## 11. File Upload

Zorunlu:
- dosya başına en fazla 10 MB,
- MIME kontrolü (izinli liste üzerinden),
- extension policy,
- random storage key,
- path traversal prevention,
- executable content block,
- content disposition güvenliği,
- authorization-protected download,
- malware scanning hook için extension point.

Image processing yapılırsa decompression bomb riskleri düşünülmelidir.

---

## 12. SQL Injection

JPA parameter binding / prepared statements kullan.

Dynamic query string concat yapılmamalıdır.

Sort/filter field whitelist edilmelidir.

---

## 13. Secrets

Secret kaynak kodda tutulmaz.

Örnek:
- DB password
- JWT signing secret/private key
- Redis password
- S3 keys

Environment/secret manager ile sağlanır.

---

## 14. Logging Safety

Loglama:
- password yok
- JWT yok
- refresh token yok
- Authorization header yok
- raw sensitive payload yok

PII minimize edilir.

---

## 15. Rate Limits

Baseline rate limitler (ayarlanabilir):
- login: 5/dakika/IP
- register: 3/saat/IP
- refresh: 30/dakika/kullanıcı
- user search: 60/dakika/kullanıcı
- friend request: 20/saat/kullanıcı
- message send: 30/dakika/kullanıcı
- typing: 60/dakika/kullanıcı
- invite create: 20/saat/kullanıcı
- invite resolve/join preview: 60/dakika/kullanıcı
- upload: 20/saat/kullanıcı

Limit aşımı `429 RATE_LIMITED` + `Retry-After` döner.

---

## 16. Security Headers

Production frontend/backend uygun yerlerde:
- CSP
- HSTS
- X-Content-Type-Options
- Referrer-Policy
- frame-ancestors / X-Frame-Options

değerlendirmelidir.

---

## 17. Dependency Security

- dependency versions pinned/controlled,
- vulnerability scanning,
- gereksiz dependency eklenmez,
- transitive dependency gözden geçirilir.

---

## 18. Threat Scenarios

Test edilmesi gereken minimum:
- stolen/expired access token
- refresh token replay
- another user's message edit
- unauthorized channel subscribe
- banned user invite join
- blocked DM
- role escalation
- owner removal
- invite race condition
- upload MIME spoof
- oversized payload
- WebSocket spam
- account enumeration
