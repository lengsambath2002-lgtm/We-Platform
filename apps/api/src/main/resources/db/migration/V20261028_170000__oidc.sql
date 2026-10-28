-- Single sign-on with VirtualBiz (docs/integrations/virtualbiz-sso.md): the platform as an OpenID Connect
-- provider (consents, authorization codes) and the Retail & Commerce module's shop links.

-- ── OpenID Connect ─────────────────────────────────────────────────────────────────
-- A person's approval for a client (configured in PLATFORM_OIDC_CLIENTS, not in the database).
CREATE TABLE oidc_consent (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    client_id   VARCHAR(100)  NOT NULL,
    scopes      VARCHAR(200)  NOT NULL,
    granted_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    UNIQUE (user_id, client_id)
);

-- Authorization codes: only the SHA-256 is stored; single use, 60 seconds.
CREATE TABLE oidc_code (
    id              UUID          PRIMARY KEY,
    code_hash       VARCHAR(64)   NOT NULL UNIQUE,
    client_id       VARCHAR(100)  NOT NULL,
    redirect_uri    VARCHAR(500)  NOT NULL,
    user_id         UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    code_challenge  VARCHAR(128)  NOT NULL,
    nonce           VARCHAR(255)  NOT NULL,
    scope           VARCHAR(200)  NOT NULL,
    auth_time       TIMESTAMPTZ   NOT NULL,
    expires_at      TIMESTAMPTZ   NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_oidc_code_expires ON oidc_code (expires_at);

-- ── Retail & Commerce: VirtualBiz shops linked to organisations ───────────────────
CREATE TABLE retail_shop_link (
    business_id    UUID          PRIMARY KEY,
    org_id         UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    business_name  VARCHAR(200)  NOT NULL,
    client_id      VARCHAR(100)  NOT NULL,
    linked_by      UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    linked_at      TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_retail_shop_link_org ON retail_shop_link (org_id);

-- Built; usable on a server only when PLATFORM_VIRTUALBIZ_URL is set (RetailGate).
UPDATE module SET status = 'AVAILABLE' WHERE key = 'retail';
