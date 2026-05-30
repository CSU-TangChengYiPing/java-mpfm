CREATE TABLE IF NOT EXISTS upload_v2_sessions (
    id UUID PRIMARY KEY,
    mount_id UUID NOT NULL REFERENCES mounts(id),
    operator VARCHAR(128) NOT NULL,
    target_path VARCHAR(1024) NOT NULL,
    filename VARCHAR(256) NOT NULL,
    total_bytes BIGINT NOT NULL,
    chunk_size_bytes BIGINT NOT NULL,
    total_chunks INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS upload_v2_parts (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES upload_v2_sessions(id) ON DELETE CASCADE,
    part_number INT NOT NULL,
    upload_ticket VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(128),
    etag VARCHAR(128),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_upload_v2_session_part UNIQUE (session_id, part_number),
    CONSTRAINT uk_upload_v2_ticket UNIQUE (upload_ticket)
);

CREATE TABLE IF NOT EXISTS upload_v2_idempotency (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    operator VARCHAR(128) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    response_json VARCHAR(8192) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_upload_v2_session_operator_status
    ON upload_v2_sessions(operator, status);
