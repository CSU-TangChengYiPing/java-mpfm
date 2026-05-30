ALTER TABLE user_transfer_governance
    ADD COLUMN IF NOT EXISTS custom_upload_bps BIGINT NOT NULL DEFAULT 0;

ALTER TABLE user_transfer_governance
    ADD COLUMN IF NOT EXISTS custom_download_bps BIGINT NOT NULL DEFAULT 0;

