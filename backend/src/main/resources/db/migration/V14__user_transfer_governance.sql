CREATE TABLE IF NOT EXISTS user_transfer_governance (
    username VARCHAR(64) PRIMARY KEY,
    upload_paused BOOLEAN NOT NULL DEFAULT FALSE,
    download_paused BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

