CREATE TABLE IF NOT EXISTS qos_policies (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    max_upload_bps BIGINT NOT NULL,
    max_download_bps BIGINT NOT NULL,
    max_concurrent_upload_tasks INTEGER NOT NULL,
    max_concurrent_download_tasks INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS qos_policy_audits (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(64) NOT NULL,
    target VARCHAR(256) NOT NULL,
    operator VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_qos_policy_audits_created_at ON qos_policy_audits(created_at DESC);

