CREATE TABLE IF NOT EXISTS share_roles (
  id UUID PRIMARY KEY,
  mount_id UUID NOT NULL REFERENCES mounts(id),
  creator_user_id UUID NOT NULL REFERENCES users(id),
  name VARCHAR(64) NOT NULL,
  is_system BOOLEAN NOT NULL DEFAULT FALSE,
  state VARCHAR(16) NOT NULL,
  role_expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_share_roles_mount_name ON share_roles(mount_id, name);

CREATE TABLE IF NOT EXISTS share_role_policies (
  id UUID PRIMARY KEY,
  role_id UUID NOT NULL REFERENCES share_roles(id),
  path_pattern VARCHAR(255) NOT NULL,
  can_visible BOOLEAN NOT NULL,
  can_read BOOLEAN NOT NULL,
  can_write BOOLEAN NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS share_links (
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

CREATE TABLE IF NOT EXISTS shared_mount_accesses (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  mount_id UUID NOT NULL REFERENCES mounts(id),
  role_id UUID NOT NULL REFERENCES share_roles(id),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  granted_by_link_id UUID NOT NULL REFERENCES share_links(id),
  granted_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_shared_mount_access_user_mount_role ON shared_mount_accesses(user_id, mount_id, role_id);
