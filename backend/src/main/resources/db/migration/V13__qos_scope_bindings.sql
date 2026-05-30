CREATE TABLE IF NOT EXISTS qos_mount_bindings (
    mount_id UUID PRIMARY KEY REFERENCES mounts(id) ON DELETE CASCADE,
    policy_id VARCHAR(64) NOT NULL REFERENCES qos_policies(id),
    updated_by VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS qos_protocol_bindings (
    protocol VARCHAR(32) PRIMARY KEY,
    policy_id VARCHAR(64) NOT NULL REFERENCES qos_policies(id),
    updated_by VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

