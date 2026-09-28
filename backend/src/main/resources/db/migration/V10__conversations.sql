CREATE TABLE conversations (
  id UUID PRIMARY KEY,
  type VARCHAR(32) NOT NULL,
  direct_pair_key VARCHAR(128) NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CHECK (type IN ('DIRECT')),
  CHECK (direct_pair_key IS NOT NULL)
);

CREATE TABLE conversation_participants (
  conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  joined_at TIMESTAMPTZ NOT NULL,
  last_read_message_id UUID NULL,
  PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX idx_conversation_participants_user
  ON conversation_participants(user_id, conversation_id);

ALTER TABLE messages
  ADD CONSTRAINT fk_messages_conversation
  FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE;
