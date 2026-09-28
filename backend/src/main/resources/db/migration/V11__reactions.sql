CREATE TABLE message_reactions (
  id UUID PRIMARY KEY,
  message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  emoji VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_message_reactions UNIQUE(message_id, user_id, emoji)
);

CREATE INDEX idx_message_reactions_message
  ON message_reactions(message_id, user_id);
