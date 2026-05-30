ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(512);
ALTER TABLE users ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(16);
ALTER TABLE users ADD COLUMN IF NOT EXISTS file_view_mode VARCHAR(16);
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS credential_updated_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS credential_version INTEGER NOT NULL DEFAULT 1;

UPDATE users
SET preferred_language = COALESCE(preferred_language, 'zh-CN'),
    file_view_mode = COALESCE(file_view_mode, 'list'),
    credential_updated_at = COALESCE(credential_updated_at, created_at),
    credential_version = COALESCE(credential_version, 1);
