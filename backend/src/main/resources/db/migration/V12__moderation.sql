CREATE TABLE community_bans (
  id UUID PRIMARY KEY,
  community_id UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  banned_by UUID NOT NULL REFERENCES users(id),
  reason VARCHAR(1000),
  expires_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE (community_id, user_id),
  CHECK (char_length(reason) <= 1000)
);

CREATE INDEX idx_community_bans_lookup ON community_bans(community_id, user_id);

CREATE TABLE audit_log (
  id UUID PRIMARY KEY,
  community_id UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
  actor_id UUID NOT NULL REFERENCES users(id),
  action VARCHAR(64) NOT NULL,
  target_user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL,
  target_id UUID NULL,
  detail VARCHAR(1000) NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_log_community ON audit_log(community_id, created_at DESC, id DESC);
