CREATE TABLE communities (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL REFERENCES users(id),
  name VARCHAR(100) NOT NULL,
  description VARCHAR(1000),
  icon_key VARCHAR,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (char_length(name) BETWEEN 2 AND 100)
);

CREATE TABLE community_members (
  id UUID PRIMARY KEY,
  community_id UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  nickname VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  joined_at TIMESTAMPTZ NOT NULL,
  UNIQUE (community_id, user_id)
);

CREATE INDEX idx_community_members_lookup ON community_members(community_id, user_id);
CREATE INDEX idx_community_members_user ON community_members(user_id);
