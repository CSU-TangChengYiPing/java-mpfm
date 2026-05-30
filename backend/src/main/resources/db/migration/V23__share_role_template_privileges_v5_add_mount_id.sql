ALTER TABLE share_role_template_privileges_v5
  ADD COLUMN IF NOT EXISTS mount_id UUID;

UPDATE share_role_template_privileges_v5 p
SET mount_id = t.mount_id
FROM share_role_templates_v5 t
WHERE p.template_id = t.id
  AND p.mount_id IS NULL;

ALTER TABLE share_role_template_privileges_v5
  ALTER COLUMN mount_id SET NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'fk_share_role_template_privileges_v5_mount'
  ) THEN
    ALTER TABLE share_role_template_privileges_v5
      ADD CONSTRAINT fk_share_role_template_privileges_v5_mount
      FOREIGN KEY (mount_id) REFERENCES mounts(id);
  END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_share_role_template_privileges_v5_mount
  ON share_role_template_privileges_v5(mount_id);

