-- Email verification: an account proves it owns its email by opening an emailed link.
ALTER TABLE app_user ADD COLUMN email_verified_at TIMESTAMPTZ;
-- Accounts from before verification keep working as they did.
UPDATE app_user SET email_verified_at = created_at;

CREATE TABLE email_verification (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX email_verification_user_idx ON email_verification (user_id);
