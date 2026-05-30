CREATE TABLE IF NOT EXISTS share_role_templates_v5 (
  id UUID PRIMARY KEY,
  mount_id UUID NOT NULL REFERENCES mounts(id),
  role_id UUID NOT NULL REFERENCES share_roles(id),
  name VARCHAR(64) NOT NULL,
  state VARCHAR(16) NOT NULL,
  default_visible BOOLEAN NOT NULL DEFAULT FALSE,
  default_read BOOLEAN NOT NULL DEFAULT FALSE,
  default_write BOOLEAN NOT NULL DEFAULT FALSE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_share_role_templates_v5_mount_name
  ON share_role_templates_v5(mount_id, name);

CREATE UNIQUE INDEX IF NOT EXISTS uk_share_role_templates_v5_role
  ON share_role_templates_v5(role_id);

CREATE TABLE IF NOT EXISTS share_role_template_privileges_v5 (
  id UUID PRIMARY KEY,
  template_id UUID NOT NULL REFERENCES share_role_templates_v5(id),
  target_path TEXT NOT NULL,
  allow_visible BOOLEAN NOT NULL DEFAULT FALSE,
  allow_read BOOLEAN NOT NULL DEFAULT FALSE,
  allow_write BOOLEAN NOT NULL DEFAULT FALSE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_share_role_template_privileges_v5_template_target
  ON share_role_template_privileges_v5(template_id, target_path);
