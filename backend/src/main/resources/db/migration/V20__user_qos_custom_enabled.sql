ALTER TABLE users
    ADD COLUMN IF NOT EXISTS qos_custom_enabled BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users
SET qos_custom_enabled = TRUE
WHERE custom_upload_bps > 0
  AND custom_download_bps > 0;
