CREATE TABLE messages (
  id UUID PRIMARY KEY,
  author_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  channel_id UUID NULL REFERENCES channels(id) ON DELETE CASCADE,
  conversation_id UUID NULL,
  reply_to_message_id UUID NULL REFERENCES messages(id) ON DELETE SET NULL,
  content TEXT NULL,
  type VARCHAR(32) NOT NULL,
  client_message_id VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  edited_at TIMESTAMPTZ NULL,
  deleted_at TIMESTAMPTZ NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (
    (channel_id IS NOT NULL AND conversation_id IS NULL)
    OR
    (channel_id IS NULL AND conversation_id IS NOT NULL)
  ),
  CHECK (type IN ('TEXT', 'SYSTEM', 'IMAGE', 'FILE')),
  CHECK (content IS NULL OR char_length(content) <= 4000),
  CONSTRAINT uq_messages_author_client UNIQUE(author_id, client_message_id)
);

CREATE INDEX idx_messages_channel_cursor
  ON messages(channel_id, created_at DESC, id DESC);
CREATE INDEX idx_messages_conversation_cursor
  ON messages(conversation_id, created_at DESC, id DESC);
