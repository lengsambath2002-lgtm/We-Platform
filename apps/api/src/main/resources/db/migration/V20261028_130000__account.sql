-- Account security and organisation settings.

-- Sessions: a refresh rotates to a new row; family_id ties the rows of one signed-in device together
-- (the id shown on the account page), started_at is when that device signed in, last_used_at its
-- latest refresh.
ALTER TABLE user_session
    ADD COLUMN family_id     UUID,
    ADD COLUMN started_at    TIMESTAMPTZ,
    ADD COLUMN last_used_at  TIMESTAMPTZ;
UPDATE user_session SET family_id = id, started_at = created_at, last_used_at = created_at;
ALTER TABLE user_session
    ALTER COLUMN family_id SET NOT NULL,
    ALTER COLUMN started_at SET NOT NULL,
    ALTER COLUMN last_used_at SET NOT NULL;
CREATE INDEX ix_user_session_family ON user_session (family_id) WHERE revoked_at IS NULL;

CREATE INDEX ix_password_reset_user ON password_reset (user_id) WHERE used_at IS NULL;

-- Organisations are soft-deleted: hidden from everyone at once, data kept, the slug stays taken.
ALTER TABLE organisation ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE organisation ADD COLUMN deleted_by UUID REFERENCES app_user (id);
