# Relayra — Deployment & Environment

## 1. Local Services

Docker Compose:
- postgres
- redis
- minio

Backend/frontend IDE veya container üzerinden çalıştırılabilir.

---

## 2. Environment Variables

Örnek:

```text
APP_ENV
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
REDIS_HOST
REDIS_PORT
JWT_SIGNING_KEY
ACCESS_TOKEN_TTL
REFRESH_TOKEN_TTL
S3_ENDPOINT
S3_BUCKET
S3_ACCESS_KEY
S3_SECRET_KEY
FRONTEND_ORIGIN
```

`.env.example` secret içermez.

---

## 3. Production Config

- debug disabled
- SQL logging disabled veya kontrollü
- secure cookies if used
- HTTPS only
- CORS restricted
- actuator restricted
- strong secret material
- DB migration controlled

---

## 4. Docker

Backend image:
- multi-stage build
- non-root user
- minimal runtime image
- healthcheck

Frontend:
- static build + secure web server/reverse proxy

---

## 5. Release Strategy

Minimum:
1. CI compile
2. tests
3. migration verification
4. security/dependency checks
5. artifact build
6. deploy
7. health check
8. smoke test

---

## 6. Database Migration

Production deploy sırasında migration sequencing önemlidir.

Backward-compatible migration tercih edilir.

Destructive schema change:
- add new
- dual-read/write gerekirse
- backfill
- switch
- remove old

şeklinde aşamalı planlanabilir.

---

## 7. Rollback

App rollback DB rollback kadar basit olmayabilir.

Migration'lar backward-compatible tasarlanmalıdır.

Critical release için rollback planı release öncesi yazılmalıdır.
