DROP INDEX IF EXISTS uq_friendships_pending_pair;

CREATE UNIQUE INDEX IF NOT EXISTS uq_friendships_pending_pair
  ON friendships(user_low_id, user_high_id)
  WHERE status = 'PENDING';
CREATE UNIQUE INDEX IF NOT EXISTS uq_friendships_accepted_pair
  ON friendships(user_low_id, user_high_id)
  WHERE status = 'ACCEPTED';
