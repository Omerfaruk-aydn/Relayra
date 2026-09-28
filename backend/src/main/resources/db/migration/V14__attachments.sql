CREATE TABLE attachments (
  id UUID PRIMARY KEY,
  message_id UUID NULL REFERENCES messages(id) ON DELETE SET NULL,
  uploaded_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  community_id UUID NULL REFERENCES communities(id) ON DELETE CASCADE,
  channel_id UUID NULL,
  conversation_id UUID NULL,
  original_filename VARCHAR(255) NOT NULL,
  storage_key VARCHAR(512) NOT NULL UNIQUE,
  mime_type VARCHAR(255) NOT NULL,
  size_bytes BIGINT NOT NULL,
  sha256 VARCHAR(64) NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CHECK (size_bytes >= 0),
  CHECK (char_length(original_filename) BETWEEN 1 AND 255)
);

CREATE INDEX idx_attachments_message ON attachments(message_id);
CREATE INDEX idx_attachments_uploader ON attachments(uploaded_by, created_at DESC);
