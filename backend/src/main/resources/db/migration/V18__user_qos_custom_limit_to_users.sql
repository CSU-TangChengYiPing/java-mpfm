ALTER TABLE users
    ADD COLUMN IF NOT EXISTS custom_upload_bps BIGINT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS custom_download_bps BIGINT NOT NULL DEFAULT 0;

UPDATE users u
SET custom_upload_bps = g.custom_upload_bps,
    custom_download_bps = g.custom_download_bps
FROM user_transfer_governance g
WHERE g.username = u.username
  AND g.custom_upload_bps > 0
  AND g.custom_download_bps > 0;
