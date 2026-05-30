ALTER TABLE users ADD COLUMN IF NOT EXISTS qos_profile VARCHAR(64);

UPDATE users
SET qos_profile = 'default'
WHERE qos_profile IS NULL OR qos_profile = '';

ALTER TABLE users ALTER COLUMN qos_profile SET DEFAULT 'default';
ALTER TABLE users ALTER COLUMN qos_profile SET NOT NULL;
