CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY,
  username VARCHAR(64) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  email VARCHAR(128),
  phone VARCHAR(32),
  platform_role VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS mounts (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL REFERENCES users(id),
  type VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  virtual_path VARCHAR(255) NOT NULL,
  physical_root VARCHAR(512) NOT NULL,
  state VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL DEFAULT 1,
  deleted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mount_owner_name_state
ON mounts(owner_id, name, state);

CREATE TABLE IF NOT EXISTS auth_sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  username VARCHAR(64) NOT NULL,
  refresh_hash VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL,
  version BIGINT NOT NULL DEFAULT 1,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_username_status
ON auth_sessions(username, status);

CREATE TABLE IF NOT EXISTS audit_logs (
  id UUID PRIMARY KEY,
  operator VARCHAR(128) NOT NULL,
  action VARCHAR(128) NOT NULL,
  target VARCHAR(256) NOT NULL,
  result VARCHAR(32) NOT NULL,
  error_code VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS file_tasks (
  id UUID PRIMARY KEY,
  action VARCHAR(128) NOT NULL,
  operator VARCHAR(128) NOT NULL,
  target VARCHAR(256) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
