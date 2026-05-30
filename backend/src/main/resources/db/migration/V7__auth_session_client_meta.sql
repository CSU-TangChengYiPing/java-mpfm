ALTER TABLE auth_sessions
    ADD COLUMN IF NOT EXISTS client_ip VARCHAR(64);

ALTER TABLE auth_sessions
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(512);
