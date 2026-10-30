-- Platform administration: the people who run the SaaS (super admins), and what they can switch off.

-- A super admin manages every organisation and account from the platform console (/admin). The first ones
-- come from PLATFORM_SUPER_ADMINS; after that, super admins grant and revoke it there.
ALTER TABLE app_user ADD COLUMN super_admin BOOLEAN NOT NULL DEFAULT false;

-- A disabled account cannot sign in and is signed out everywhere; nothing else about it changes.
ALTER TABLE app_user
    ADD COLUMN disabled_at      TIMESTAMPTZ,
    ADD COLUMN disabled_reason  VARCHAR(500);

-- A suspended organisation is closed to its members (e.g. unpaid, abuse) until reactivated; data is kept.
ALTER TABLE organisation
    ADD COLUMN suspended_at      TIMESTAMPTZ,
    ADD COLUMN suspended_by      UUID REFERENCES app_user (id),
    ADD COLUMN suspended_reason  VARCHAR(500);

-- What super admins did. Not tied to an organisation (the org audit log is), and kept when one is deleted.
CREATE TABLE platform_audit (
    id           UUID          PRIMARY KEY,
    actor_id     UUID          REFERENCES app_user (id),
    action       VARCHAR(40)   NOT NULL,
    target_type  VARCHAR(20)   NOT NULL CHECK (target_type IN ('organisation', 'user')),
    target_id    UUID          NOT NULL,
    data         JSONB         NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_platform_audit_created ON platform_audit (created_at DESC);
CREATE INDEX ix_platform_audit_target ON platform_audit (target_type, target_id, created_at DESC);
