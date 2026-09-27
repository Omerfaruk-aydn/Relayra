# Relayra — Implementation Plan

## Phase 0 — Readiness Review

Önce tüm docs okunur.

Kontrol:
- çelişki,
- eksik domain kuralı,
- auth belirsizliği,
- API/DB mismatch,
- permission gap,
- concurrency risk,
- test gap.

Exit criteria:
- kritik açık issue yok.

---

## Phase 1 — Repository & Bootstrap

- monorepo structure
- Spring Boot
- React/Vite
- Docker Compose
- PostgreSQL
- Redis
- MinIO
- Flyway
- Actuator
- env config
- CI skeleton

Exit:
- backend boot
- frontend boot
- migration passes
- health works

---

## Phase 2 — Identity & Auth

- user
- profile
- password hashing
- register
- login
- access token
- refresh rotation
- logout
- revoke
- rate limit
- tests

Exit:
- full auth tests pass
- no token/plain password leakage

---

## Phase 3 — User/Profile/Search

- profile CRUD
- normalized username/email
- bounded search
- avatar metadata

---

## Phase 4 — Friendship & Block

- request
- cancel
- accept
- reject
- remove
- block/unblock
- duplicate/concurrent rules

---

## Phase 5 — Community Core

- create
- update
- delete
- owner membership
- default role
- default channel
- leave
- ownership transfer policy

---

## Phase 6 — Invite & Membership

- create invite
- resolve
- join
- revoke
- max uses
- expiration
- ban integration
- race-condition test

---

## Phase 7 — Channel

- create
- update
- delete
- reorder
- permission checks

---

## Phase 8 — Roles & Permissions

Önce permission engine.
Sonra role CRUD ve assignment.

Security regression testleri zorunlu.

---

## Phase 9 — Message Persistence

- send service
- history
- cursor pagination
- reply
- edit
- soft delete
- idempotency
- message constraints

---

## Phase 10 — WebSocket

- connect auth
- subscribe auth
- message send
- broadcast
- protocol error
- tests

---

## Phase 11 — Presence & Typing

- multi-connection presence
- Redis TTL
- typing throttle
- disconnect grace period

---

## Phase 12 — DM

- canonical direct conversation
- participant rules
- block integration
- realtime private delivery

---

## Phase 13 — Reaction

- add/remove
- idempotency
- deleted message rule
- realtime broadcast

---

## Phase 14 — Moderation

- kick
- ban
- unban
- hierarchy checks
- audit log

---

## Phase 15 — Notification & Mention

- mention parse
- notification create
- read state
- private push

---

## Phase 16 — Attachment

- upload
- validation
- object storage
- protected access
- delete policy

---

## Phase 17 — Frontend Product Completion

- auth pages
- home/friends
- DM
- community
- channel
- members
- settings
- role UI
- moderation UI
- all loading/error/empty/offline states

---

## Phase 18 — Testing Hardening

- unit
- integration
- security
- concurrency
- WebSocket
- frontend
- E2E

---

## Phase 19 — Observability & Performance

- structured logs
- request IDs
- metrics
- query review
- N+1 review
- indexes
- connection pool
- payload bounds

---

## Phase 20 — Release Readiness

Checklist:
- no critical TODO
- docs current
- env example current
- migrations pass
- clean DB boot
- test suite green
- dependency scan
- security negative tests green
- two-browser realtime acceptance green
