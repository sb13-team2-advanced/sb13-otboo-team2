ALTER TABLE auth_sessions
    ADD COLUMN updated_at TIMESTAMP;

UPDATE auth_sessions
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE auth_sessions
    ALTER COLUMN updated_at SET NOT NULL;

CREATE INDEX idx_auth_sessions_user_active
    ON auth_sessions (user_id, expires_at)
    WHERE revoked_at IS NULL;