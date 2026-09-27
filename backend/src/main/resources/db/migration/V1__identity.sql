CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id UUID PRIMARY KEY,
  username VARCHAR(32) NOT NULL,
  username_normalized VARCHAR(32) NOT NULL UNIQUE,
  email VARCHAR(320) NOT NULL,
  email_normalized VARCHAR(320) NOT NULL UNIQUE,
  password_hash VARCHAR NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  last_seen_at TIMESTAMPTZ NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE profiles (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  display_name VARCHAR(64),
  bio VARCHAR(500),
  avatar_key VARCHAR,
  banner_key VARCHAR,
  timezone VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR NOT NULL UNIQUE,
  token_family_id UUID NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL,
  replaced_by UUID NULL,
  user_agent_hash VARCHAR NULL,
  ip_hash VARCHAR NULL
);

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(token_family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
