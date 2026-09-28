CREATE TABLE channels (
  id UUID PRIMARY KEY,
  community_id UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(250),
  type VARCHAR(32) NOT NULL,
  position INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (char_length(name) BETWEEN 2 AND 100),
  CHECK (type IN ('TEXT')),
  CHECK (position >= 0)
);

CREATE INDEX idx_channels_community_position ON channels(community_id, position);

INSERT INTO channels (
  id, community_id, name, description, type, position, created_at, updated_at, version
)
SELECT
  gen_random_uuid(), id, 'general', NULL, 'TEXT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM communities;
