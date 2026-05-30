ALTER TABLE file_tasks
    ADD COLUMN IF NOT EXISTS transferred_bytes BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_bytes BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS chunk_size_bytes BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_chunks INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS completed_chunks INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS failed_chunks INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS chunk_states_json TEXT;

CREATE TABLE IF NOT EXISTS upload_sessions (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES file_tasks(id) ON DELETE CASCADE,
    mount_id UUID NOT NULL REFERENCES mounts(id),
    operator VARCHAR(128) NOT NULL,
    target_dir VARCHAR(512) NOT NULL,
    filename VARCHAR(256) NOT NULL,
    total_bytes BIGINT NOT NULL,
    chunk_size_bytes BIGINT NOT NULL,
    total_chunks INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS upload_chunks (
    id UUID PRIMARY KEY,
    upload_id UUID NOT NULL REFERENCES upload_sessions(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    checksum VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_upload_chunks_upload_index
    ON upload_chunks(upload_id, chunk_index);

CREATE INDEX IF NOT EXISTS idx_upload_sessions_operator_status
    ON upload_sessions(operator, status);
