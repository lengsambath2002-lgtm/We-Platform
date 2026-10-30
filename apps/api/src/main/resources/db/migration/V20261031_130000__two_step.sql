-- Two-step sign-in: a code from an authenticator app (TOTP, RFC 6238) after the password.
ALTER TABLE app_user
    ADD COLUMN totp_secret         VARCHAR(200),
    ADD COLUMN totp_pending_secret VARCHAR(200),
    ADD COLUMN totp_enabled_at     TIMESTAMPTZ,
    -- The last 30-second step a code was used for: a code works once.
    ADD COLUMN totp_last_step      BIGINT;

-- Single-use codes for when the phone is lost; only their SHA-256 is stored.
CREATE TABLE two_step_recovery_code (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX two_step_recovery_code_user_idx ON two_step_recovery_code (user_id);

-- The password was right; the code is still to come (5 minutes).
CREATE TABLE two_step_challenge (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX two_step_challenge_user_idx ON two_step_challenge (user_id);
