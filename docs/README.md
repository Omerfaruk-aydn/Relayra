# Relayra

Relayra, gerçek zamanlı mesajlaşma, topluluk yönetimi, arkadaşlık, moderasyon, rol/izin sistemi ve güvenli dosya paylaşımı sunan web tabanlı bir iletişim platformudur.

Bu repository için hedef yalnızca "çalışan bir uygulama" üretmek değildir. Hedef; Java/Spring Boot merkezli, üretim ortamına yakın prensiplerle tasarlanmış, test edilebilir, gözlemlenebilir, güvenli ve uzun vadede geliştirilebilir bir sistem oluşturmaktır.

---

## Ana Teknoloji Yığını

### Backend
- Java 21+
- Spring Boot 3.x
- Spring Web
- Spring Security
- Spring Data JPA
- Hibernate
- Spring WebSocket
- Jakarta Bean Validation
- Spring Actuator
- Flyway

### Frontend
- React
- TypeScript
- Vite
- React Router
- TanStack Query
- Zustand
- React Hook Form
- Zod
- STOMP/WebSocket Client

### Veri ve Altyapı
- PostgreSQL
- Redis
- MinIO / S3-compatible object storage
- Docker
- Docker Compose

### Test
- JUnit 5
- Mockito
- Spring Boot Test
- Testcontainers

---

## Mimari Karar

İlk sürüm **Modular Monolith** olacaktır.

Mikroservis, Kubernetes veya gereksiz distributed-system karmaşıklığı ile başlanmayacaktır.

Ana prensip:

> Clean boundaries. Strong consistency. Explicit security. Observable behavior. Production-minded engineering.

---

## Dokümantasyon Haritası

| Dosya | Amaç |
|---|---|
| `APP.md` | Uygulamanın tamamını tek yerde açıklayan ana ürün dosyası |
| `PROJECT.md` | Kapsam, modüller, özellikler, MVP ve non-goals |
| `ARCHITECTURE.md` | Sistem mimarisi, modüller, veri akışları, bağımlılıklar |
| `DOMAIN_RULES.md` | Kritik iş kuralları, invariant'lar ve edge-case davranışları |
| `DATABASE.md` | Şema, ilişkiler, constraint'ler, index ve migration kuralları |
| `API.md` | REST sözleşmesi, hata modeli, pagination, idempotency |
| `WEBSOCKET.md` | Realtime protokolü, event contract, reconnect ve ordering |
| `SECURITY.md` | AuthN/AuthZ, token güvenliği, upload güvenliği, abuse prevention |
| `TESTING_QA.md` | Unit/integration/security/e2e/realtime test stratejisi |
| `OBSERVABILITY.md` | Logging, metrics, tracing, health checks, alerting yaklaşımı |
| `DEPLOYMENT.md` | Environment, Docker, config, secrets, release yaklaşımı |
| `UI_UX.md` | Ekranlar, tasarım sistemi, state'ler ve interaction kuralları |
| `IMPLEMENTATION_PLAN.md` | Fazlara bölünmüş geliştirme planı ve exit criteria |
| `MASTER_PROMPT.md` | Coding agent için ana çalışma talimatı |

---

## Geliştirme Kuralı

Her özellik için aşağıdaki sıra izlenmelidir:

1. Gereksinim ve domain kuralı doğrulanır.
2. Database etkisi belirlenir.
3. API / WebSocket contract'ı tanımlanır.
4. Authorization matrisi kontrol edilir.
5. Concurrency ve idempotency riski değerlendirilir.
6. Kod yazılır.
7. Unit test yazılır.
8. Integration test yazılır.
9. Security ve negatif testler yazılır.
10. Dokümantasyon güncellenir.
11. Acceptance criteria sağlanmadan özellik tamamlanmış sayılmaz.

---

## Kalite Notu

Hiçbir yazılım için "sıfır bug" garantisi teknik olarak verilemez. Relayra'nın amacı bug ve güvenlik açığı riskini azaltmak için:

- database constraint'leri,
- explicit authorization,
- idempotency,
- transaction sınırları,
- concurrency kontrolleri,
- kapsamlı testler,
- structured logging,
- observability,
- güvenli varsayılanlar

kullanmaktır.
