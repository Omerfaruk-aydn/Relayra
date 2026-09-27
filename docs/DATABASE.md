# Relayra — Database Specification

## 1. Genel

Database: PostgreSQL  
Migration: Flyway  
ID: UUID  
Timestamp: TIMESTAMPTZ / UTC  
Hibernate production strategy: `ddl-auto=validate`

---

## 2. Temel Tablolar

### users
```text
id UUID PK
username VARCHAR(32) NOT NULL
username_normalized VARCHAR(32) NOT NULL UNIQUE
email VARCHAR(320) NOT NULL
email_normalized VARCHAR(320) NOT NULL UNIQUE
password_hash VARCHAR NOT NULL
status VARCHAR(32) NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
last_seen_at TIMESTAMPTZ NULL
version BIGINT NOT NULL DEFAULT 0
```

- `username` 3-32 karakter, `^[a-zA-Z0-9_.]+$` desenindedir; `username_normalized` lowercase halidir.
- `email` trim + lowercase normalize edilir; karşılaştırma case-insensitive yapılır.

### profiles
```text
id UUID PK
user_id UUID NOT NULL UNIQUE FK users(id)
display_name VARCHAR(64)
bio VARCHAR(500)
avatar_key VARCHAR
banner_key VARCHAR
timezone VARCHAR(64)
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

### friendships
```text
id UUID PK
sender_id UUID NOT NULL FK users(id)
receiver_id UUID NOT NULL FK users(id)
status VARCHAR(32) NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
CHECK(sender_id <> receiver_id)
```

Karşılıklı duplicate ve concurrent request kontrolü için canonical pair alanları zorunludur:
```text
user_low_id UUID NOT NULL FK users(id)
user_high_id UUID NOT NULL FK users(id)
```

- `user_low_id` / `user_high_id`, `sender_id` ve `receiver_id`'nin sıralanmış halidir.
- Partial unique index: `UNIQUE(user_low_id, user_high_id) WHERE status = 'PENDING'`.
- Ters yönde bekleyen istek varken yeni istek uygulama seviyesinde `409 DUPLICATE_RESOURCE` ile reddedilir; otomatik accept yapılmaz.

### user_blocks
```text
id UUID PK
blocker_id UUID NOT NULL FK users(id)
blocked_id UUID NOT NULL FK users(id)
created_at TIMESTAMPTZ NOT NULL
UNIQUE(blocker_id, blocked_id)
CHECK(blocker_id <> blocked_id)
```

Block, friendship ve DM kontrollerinde authoritative kaynaktır.

### communities
```text
id UUID PK
owner_id UUID NOT NULL FK users(id)
name VARCHAR(100) NOT NULL
description VARCHAR(1000)
icon_key VARCHAR
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
version BIGINT NOT NULL DEFAULT 0
```

### community_members
```text
id UUID PK
community_id UUID NOT NULL FK communities(id)
user_id UUID NOT NULL FK users(id)
nickname VARCHAR(64)
status VARCHAR(32) NOT NULL
joined_at TIMESTAMPTZ NOT NULL
UNIQUE(community_id, user_id)
```

### channels
```text
id UUID PK
community_id UUID NOT NULL FK communities(id)
name VARCHAR(100) NOT NULL
description VARCHAR(250)
type VARCHAR(32) NOT NULL
position INTEGER NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
version BIGINT NOT NULL DEFAULT 0
```

### conversations
```text
id UUID PK
type VARCHAR(32) NOT NULL
direct_pair_key VARCHAR NULL UNIQUE
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

### conversation_participants
```text
conversation_id UUID NOT NULL FK conversations(id)
user_id UUID NOT NULL FK users(id)
joined_at TIMESTAMPTZ NOT NULL
last_read_message_id UUID NULL
PRIMARY KEY(conversation_id, user_id)
```

### messages
```text
id UUID PK
author_id UUID NOT NULL FK users(id)
channel_id UUID NULL FK channels(id)
conversation_id UUID NULL FK conversations(id)
reply_to_message_id UUID NULL FK messages(id)
content TEXT NULL
type VARCHAR(32) NOT NULL
client_message_id VARCHAR(128) NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
edited_at TIMESTAMPTZ NULL
deleted_at TIMESTAMPTZ NULL
version BIGINT NOT NULL DEFAULT 0
CHECK(
  (channel_id IS NOT NULL AND conversation_id IS NULL)
  OR
  (channel_id IS NULL AND conversation_id IS NOT NULL)
)
UNIQUE(author_id, client_message_id)
```

- `content`, TEXT tipi mesajlarda en fazla 4000 karakterdir; boş/whitespace-only içerik reddedilir.
- `TEXT` tipi mesaj `content` ister; `FILE`/`IMAGE` tipi mesaj en az bir attachment ister.

### message_reactions
```text
id UUID PK
message_id UUID NOT NULL FK messages(id)
user_id UUID NOT NULL FK users(id)
emoji VARCHAR(64) NOT NULL
created_at TIMESTAMPTZ NOT NULL
UNIQUE(message_id, user_id, emoji)
```

### attachments
```text
id UUID PK
message_id UUID NOT NULL FK messages(id)
original_filename VARCHAR(255) NOT NULL
storage_key VARCHAR(512) NOT NULL UNIQUE
mime_type VARCHAR(255) NOT NULL
size_bytes BIGINT NOT NULL
sha256 VARCHAR(64) NULL
created_at TIMESTAMPTZ NOT NULL
CHECK(size_bytes >= 0)
```

### roles
```text
id UUID PK
community_id UUID NOT NULL FK communities(id)
name VARCHAR(64) NOT NULL
position INTEGER NOT NULL
color VARCHAR(16)
managed BOOLEAN NOT NULL DEFAULT FALSE
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
UNIQUE(community_id, name)
```

### role_permissions
```text
role_id UUID NOT NULL FK roles(id)
permission VARCHAR(64) NOT NULL
PRIMARY KEY(role_id, permission)
```

### member_roles
```text
community_member_id UUID NOT NULL FK community_members(id)
role_id UUID NOT NULL FK roles(id)
PRIMARY KEY(community_member_id, role_id)
```

### invites
```text
id UUID PK
community_id UUID NOT NULL FK communities(id)
created_by UUID NOT NULL FK users(id)
code VARCHAR(64) NOT NULL UNIQUE
max_uses INTEGER NULL
usage_count INTEGER NOT NULL DEFAULT 0
expires_at TIMESTAMPTZ NULL
revoked_at TIMESTAMPTZ NULL
created_at TIMESTAMPTZ NOT NULL
version BIGINT NOT NULL DEFAULT 0
CHECK(usage_count >= 0)
CHECK(max_uses IS NULL OR max_uses > 0)
CHECK(max_uses IS NULL OR usage_count <= max_uses)
```

### bans
```text
id UUID PK
community_id UUID NOT NULL FK communities(id)
user_id UUID NOT NULL FK users(id)
banned_by UUID NOT NULL FK users(id)
reason VARCHAR(1000)
created_at TIMESTAMPTZ NOT NULL
expires_at TIMESTAMPTZ NULL
UNIQUE(community_id, user_id)
```

### notifications
```text
id UUID PK
user_id UUID NOT NULL FK users(id)
actor_id UUID NULL FK users(id)
type VARCHAR(64) NOT NULL
reference_id UUID NULL
is_read BOOLEAN NOT NULL DEFAULT FALSE
created_at TIMESTAMPTZ NOT NULL
```

### channel_read_states
```text
user_id UUID NOT NULL FK users(id)
channel_id UUID NOT NULL FK channels(id)
last_read_message_id UUID NULL
updated_at TIMESTAMPTZ NOT NULL
PRIMARY KEY(user_id, channel_id)
```

### refresh_tokens
```text
id UUID PK
user_id UUID NOT NULL FK users(id)
token_hash VARCHAR NOT NULL UNIQUE
token_family_id UUID NOT NULL
expires_at TIMESTAMPTZ NOT NULL
revoked_at TIMESTAMPTZ NULL
created_at TIMESTAMPTZ NOT NULL
replaced_by UUID NULL
user_agent_hash VARCHAR NULL
ip_hash VARCHAR NULL
```

### audit_logs
```text
id UUID PK
community_id UUID NULL FK communities(id)
actor_id UUID NULL FK users(id)
action VARCHAR(64) NOT NULL
target_id UUID NULL
metadata JSONB NOT NULL DEFAULT '{}'::jsonb
created_at TIMESTAMPTZ NOT NULL
```

---

## 3. Kritik Indexler

```text
messages(channel_id, created_at DESC, id DESC)
messages(conversation_id, created_at DESC, id DESC)
community_members(community_id, user_id)
friendships(sender_id, receiver_id)
friendships(receiver_id, status)
friendships(user_low_id, user_high_id) WHERE status = 'PENDING' UNIQUE
user_blocks(blocker_id, blocked_id)
community_members(community_id, status)
notifications(user_id, is_read, created_at DESC)
invites(code)
bans(community_id, user_id)
audit_logs(community_id, created_at DESC)
```

---

## 4. Pagination

Message pagination:
- OFFSET kullanılmamalı.
- Cursor `(created_at, id)` tabanlı olabilir.

Örnek:
```text
beforeCreatedAt
beforeId
limit
```

---

## 5. Locking

Gerekli alanlar:
- invite usage increment
- role reorder
- channel reorder
- ownership transfer

Duruma göre:
- optimistic version,
- `SELECT ... FOR UPDATE`,
- atomic update

kullanılmalıdır.

---

## 6. Migration Kuralları

- Migration immutable olmalıdır.
- Production'a uygulanmış migration değiştirilmez.
- Yeni migration eklenir.
- destructive migration iki aşamalı planlanır.
- backfill gerekiyorsa bounded batch kullanılır.
