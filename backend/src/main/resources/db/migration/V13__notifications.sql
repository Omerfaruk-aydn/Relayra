CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type VARCHAR(32) NOT NULL,
  actor_id UUID NULL REFERENCES users(id) ON DELETE SET NULL,
  community_id UUID NULL REFERENCES communities(id) ON DELETE CASCADE,
  channel_id UUID NULL,
  conversation_id UUID NULL,
  message_id UUID NULL,
  reference_id UUID NULL,
  detail VARCHAR(1000) NULL,
  read_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CHECK (char_length(detail) <= 1000),
  UNIQUE NULLS NOT DISTINCT (user_id, type, actor_id, message_id)
);

CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC, id DESC);
CREATE INDEX idx_notifications_unread ON notifications(user_id, read_at, created_at DESC, id DESC);
