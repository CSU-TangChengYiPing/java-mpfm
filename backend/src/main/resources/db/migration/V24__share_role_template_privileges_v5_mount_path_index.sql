CREATE INDEX IF NOT EXISTS idx_share_role_template_privileges_v5_mount_target
  ON share_role_template_privileges_v5(mount_id, target_path);

