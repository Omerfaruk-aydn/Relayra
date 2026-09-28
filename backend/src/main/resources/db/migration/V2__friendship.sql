CREATE TABLE friendships (
  id UUID PRIMARY KEY,
  sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  receiver_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  user_low_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  user_high_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CHECK (sender_id <> receiver_id),
  CHECK (user_low_id <> user_high_id)
);

CREATE UNIQUE INDEX uq_friendships_pending_pair
  ON friendships(user_low_id, user_high_id)
  WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_friendships_accepted_pair
  ON friendships(user_low_id, user_high_id)
  WHERE status = 'ACCEPTED';
CREATE INDEX idx_friendships_sender ON friendships(sender_id, receiver_id);
CREATE INDEX idx_friendships_receiver ON friendships(receiver_id, status);

CREATE TABLE user_blocks (
  id UUID PRIMARY KEY,
  blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE (blocker_id, blocked_id),
  CHECK (blocker_id <> blocked_id)
);

CREATE INDEX idx_user_blocks_pair ON user_blocks(blocker_id, blocked_id);
