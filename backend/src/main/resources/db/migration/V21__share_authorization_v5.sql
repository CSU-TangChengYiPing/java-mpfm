CREATE TABLE IF NOT EXISTS share_links_v5 (
  id UUID PRIMARY KEY,
  mount_id UUID NOT NULL REFERENCES mounts(id),
  role_id UUID NOT NULL REFERENCES share_roles(id),
  token VARCHAR(128) NOT NULL UNIQUE,
  state VARCHAR(16) NOT NULL,
  start_at TIMESTAMPTZ,
  expire_at TIMESTAMPTZ,
  max_uses INTEGER,
  used_count INTEGER NOT NULL DEFAULT 0,
  role_start_at TIMESTAMPTZ,
  role_expire_at TIMESTAMPTZ,
  created_by_user_id UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS shared_mount_accesses_v5 (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  mount_id UUID NOT NULL REFERENCES mounts(id),
  role_id UUID NOT NULL REFERENCES share_roles(id),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  granted_by_link_id UUID NOT NULL REFERENCES share_links_v5(id),
  granted_at TIMESTAMPTZ NOT NULL,
  role_start_at TIMESTAMPTZ,
  role_expire_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_shared_mount_accesses_v5_user_mount_role
  ON shared_mount_accesses_v5(user_id, mount_id, role_id);

