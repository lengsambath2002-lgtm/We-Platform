-- Custom roles: a named set of permissions from the platform's catalogue, granted to members (never to guests) for
-- the whole organisation or for one unit and everything below it. Owners and Admins already hold every permission.

CREATE TABLE org_role (
    id           UUID          PRIMARY KEY,
    org_id       UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name         VARCHAR(60)   NOT NULL,
    description  VARCHAR(300),
    permissions  JSONB         NOT NULL DEFAULT '[]',
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX org_role_name ON org_role (org_id, lower(name));

CREATE TABLE org_role_grant (
    id             UUID          PRIMARY KEY,
    org_id         UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    role_id        UUID          NOT NULL REFERENCES org_role (id) ON DELETE CASCADE,
    membership_id  UUID          NOT NULL REFERENCES membership (id) ON DELETE CASCADE,
    unit_id        UUID          REFERENCES org_unit (id),
    granted_by     UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX org_role_grant_once ON org_role_grant (role_id, membership_id, COALESCE(unit_id, role_id));
CREATE INDEX org_role_grant_member ON org_role_grant (membership_id);
CREATE INDEX org_role_grant_unit ON org_role_grant (unit_id);
