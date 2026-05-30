ALTER TABLE users
    ADD COLUMN IF NOT EXISTS upload_paused BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS download_paused BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users u
SET upload_paused = g.upload_paused,
    download_paused = g.download_paused
FROM user_transfer_governance g
WHERE g.username = u.username;
