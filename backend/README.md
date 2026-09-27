# Relayra bootstrap

Phase 1 exit criteria:
- backend boots on JDK 21+ (`mvn -f backend/pom.xml spring-boot:run`)
- frontend boots (`npm run dev` in `frontend/`)
- `docker compose up -d` provides postgres, redis, minio
- Flyway validates migrations against a clean database on boot
- `GET /api/v1/health` returns `{"status":"UP"}`
