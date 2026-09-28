CREATE TABLE invites (
  id UUID PRIMARY KEY,
  community_id UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
  created_by UUID NOT NULL REFERENCES users(id),
  code VARCHAR(64) NOT NULL UNIQUE,
  max_uses INTEGER NULL,
  usage_count INTEGER NOT NULL DEFAULT 0,
  expires_at TIMESTAMPTZ NULL,
  revoked_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (usage_count >= 0),
  CHECK (max_uses IS NULL OR max_uses > 0),
  CHECK (max_uses IS NULL OR usage_count <= max_uses)
);

CREATE INDEX idx_invites_community ON invites(community_id);
