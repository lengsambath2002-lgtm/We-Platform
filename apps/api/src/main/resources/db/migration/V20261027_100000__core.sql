-- Business Platform core (docs/02-architecture.md §8): identity, organisations, modules, projects &
-- tasks, workflow (approvals), calendar, activity, notifications, files. Every org-scoped table has
-- org_id; ids are UUIDs; times are TIMESTAMPTZ (UTC).

-- ── Identity ───────────────────────────────────────────────────────────────────────
CREATE TABLE app_user (
    id             UUID          PRIMARY KEY,
    email          VARCHAR(254)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100)  NOT NULL,
    name           VARCHAR(80)   NOT NULL,
    locale         VARCHAR(5)    NOT NULL DEFAULT 'en' CHECK (locale IN ('en', 'km')),
    avatar_url     VARCHAR(500),
    failed_logins  SMALLINT      NOT NULL DEFAULT 0,
    locked_until   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE user_session (
    id                  UUID          PRIMARY KEY,
    user_id             UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    refresh_token_hash  VARCHAR(64)   NOT NULL UNIQUE,
    user_agent          VARCHAR(300),
    ip                  VARCHAR(64),
    expires_at          TIMESTAMPTZ   NOT NULL,
    revoked_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_user_session_user ON user_session (user_id);

CREATE TABLE password_reset (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── Organisations ──────────────────────────────────────────────────────────────────
CREATE TABLE organisation (
    id          UUID          PRIMARY KEY,
    slug        VARCHAR(40)   NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{1,38}[a-z0-9])$'),
    name        VARCHAR(120)  NOT NULL,
    created_by  UUID          NOT NULL REFERENCES app_user (id),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE branch (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name        VARCHAR(120)  NOT NULL,
    address     VARCHAR(300),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, name)
);

CREATE TABLE membership (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    user_id     UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role        VARCHAR(10)   NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'GUEST')),
    status      VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    title       VARCHAR(80),
    branch_id   UUID          REFERENCES branch (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, user_id)
);
CREATE INDEX ix_membership_user ON membership (user_id);

CREATE TABLE invitation (
    id           UUID          PRIMARY KEY,
    org_id       UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    email        VARCHAR(254)  NOT NULL,
    role         VARCHAR(10)   NOT NULL CHECK (role IN ('ADMIN', 'MEMBER', 'GUEST')),
    token_hash   VARCHAR(64)   NOT NULL UNIQUE,
    invited_by   UUID          NOT NULL REFERENCES app_user (id),
    expires_at   TIMESTAMPTZ   NOT NULL,
    accepted_at  TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_invitation_org ON invitation (org_id);

CREATE TABLE team (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name        VARCHAR(80)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, name)
);

CREATE TABLE team_member (
    team_id        UUID  NOT NULL REFERENCES team (id) ON DELETE CASCADE,
    membership_id  UUID  NOT NULL REFERENCES membership (id) ON DELETE CASCADE,
    PRIMARY KEY (team_id, membership_id)
);

-- ── Modules (registry, not hard-coded) ─────────────────────────────────────────────
CREATE TABLE module (
    key          VARCHAR(20)   PRIMARY KEY,
    name_en      VARCHAR(60)   NOT NULL,
    name_km      VARCHAR(60)   NOT NULL,
    kind         VARCHAR(10)   NOT NULL CHECK (kind IN ('CORE', 'INDUSTRY')),
    status       VARCHAR(12)   NOT NULL CHECK (status IN ('AVAILABLE', 'COMING_SOON')),
    sort_order   SMALLINT      NOT NULL
);

INSERT INTO module (key, name_en, name_km, kind, status, sort_order) VALUES
    ('projects',  'Projects & Tasks',          'គម្រោង និងការងារ',           'CORE',     'AVAILABLE',   10),
    ('business',  'General Business',          'គ្រប់គ្រងអាជីវកម្មទូទៅ',     'INDUSTRY', 'COMING_SOON', 20),
    ('education', 'Education',                 'គ្រប់គ្រងការអប់រំ',          'INDUSTRY', 'COMING_SOON', 30),
    ('retail',    'Retail & Commerce',         'លក់រាយ និងពាណិជ្ជកម្ម',       'INDUSTRY', 'COMING_SOON', 40),
    ('hr',        'HR & Workforce',            'ធនធានមនុស្ស និងកម្លាំងពលកម្ម', 'INDUSTRY', 'COMING_SOON', 50),
    ('service',   'Service & Operations',      'សេវាកម្ម និងប្រតិបត្តិការ',   'INDUSTRY', 'COMING_SOON', 60);

CREATE TABLE org_module (
    org_id      UUID         NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    module_key  VARCHAR(20)  NOT NULL REFERENCES module (key),
    enabled     BOOLEAN      NOT NULL,
    changed_by  UUID         REFERENCES app_user (id),
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, module_key)
);

-- ── Projects & tasks ───────────────────────────────────────────────────────────────
CREATE TABLE project (
    id           UUID          PRIMARY KEY,
    org_id       UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    key          VARCHAR(10)   NOT NULL CHECK (key ~ '^[A-Z][A-Z0-9]{1,9}$'),
    name         VARCHAR(120)  NOT NULL,
    description  TEXT,
    status       VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ON_HOLD', 'DONE', 'ARCHIVED')),
    visibility   VARCHAR(8)    NOT NULL DEFAULT 'ORG' CHECK (visibility IN ('ORG', 'PRIVATE')),
    lead_id      UUID          REFERENCES app_user (id),
    start_date   DATE,
    due_date     DATE,
    next_number  INTEGER       NOT NULL DEFAULT 1,
    created_by   UUID          NOT NULL REFERENCES app_user (id),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, key)
);

CREATE TABLE project_member (
    project_id  UUID         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    user_id     UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role        VARCHAR(8)   NOT NULL CHECK (role IN ('LEAD', 'MEMBER', 'VIEWER')),
    added_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id)
);
CREATE INDEX ix_project_member_user ON project_member (user_id);

CREATE TABLE task (
    id            UUID          PRIMARY KEY,
    org_id        UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    project_id    UUID          NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    number        INTEGER       NOT NULL,
    parent_id     UUID          REFERENCES task (id) ON DELETE CASCADE,
    title         VARCHAR(200)  NOT NULL,
    description   TEXT,
    status        VARCHAR(12)   NOT NULL DEFAULT 'TODO' CHECK (status IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE')),
    priority      VARCHAR(8)    NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    assignee_id   UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    reporter_id   UUID          NOT NULL REFERENCES app_user (id),
    start_date    DATE,
    due_date      DATE,
    position      DOUBLE PRECISION NOT NULL DEFAULT 0,
    completed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (project_id, number)
);
CREATE INDEX ix_task_org_assignee ON task (org_id, assignee_id) WHERE completed_at IS NULL;
CREATE INDEX ix_task_project_status ON task (project_id, status, position);
CREATE INDEX ix_task_parent ON task (parent_id);
CREATE INDEX ix_task_due ON task (org_id, due_date) WHERE due_date IS NOT NULL;

CREATE TABLE task_comment (
    id          UUID         PRIMARY KEY,
    org_id      UUID         NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    task_id     UUID         NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    author_id   UUID         NOT NULL REFERENCES app_user (id),
    body        TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    edited_at   TIMESTAMPTZ
);
CREATE INDEX ix_task_comment_task ON task_comment (task_id, created_at);

-- ── Files ──────────────────────────────────────────────────────────────────────────
CREATE TABLE stored_file (
    id            UUID          PRIMARY KEY,
    org_id        UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    owner_id      UUID          NOT NULL REFERENCES app_user (id),
    name          VARCHAR(255)  NOT NULL,
    content_type  VARCHAR(120)  NOT NULL,
    size_bytes    BIGINT        NOT NULL CHECK (size_bytes > 0),
    storage_key   VARCHAR(200)  NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE task_attachment (
    task_id   UUID  NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    file_id   UUID  NOT NULL REFERENCES stored_file (id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, file_id)
);

-- ── Workflow (generic approvals) ───────────────────────────────────────────────────
CREATE TABLE workflow_definition (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    module_key  VARCHAR(20)   NOT NULL REFERENCES module (key),
    name        VARCHAR(120)  NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by  UUID          NOT NULL REFERENCES app_user (id),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, name)
);

CREATE TABLE workflow_step_def (
    id             UUID         PRIMARY KEY,
    workflow_id    UUID         NOT NULL REFERENCES workflow_definition (id) ON DELETE CASCADE,
    step_order     SMALLINT     NOT NULL,
    approver_type  VARCHAR(14)  NOT NULL CHECK (approver_type IN ('USER', 'ORG_ROLE', 'PROJECT_LEAD')),
    approver_user  UUID         REFERENCES app_user (id),
    approver_role  VARCHAR(10)  CHECK (approver_role IN ('OWNER', 'ADMIN')),
    UNIQUE (workflow_id, step_order)
);

CREATE TABLE approval_request (
    id            UUID          PRIMARY KEY,
    org_id        UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    workflow_id   UUID          NOT NULL REFERENCES workflow_definition (id),
    module_key    VARCHAR(20)   NOT NULL REFERENCES module (key),
    record_type   VARCHAR(30)   NOT NULL,
    record_id     UUID          NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    summary       TEXT,
    requester_id  UUID          NOT NULL REFERENCES app_user (id),
    status        VARCHAR(18)   NOT NULL CHECK (status IN ('PENDING', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    current_step  SMALLINT,
    decided_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_approval_record ON approval_request (org_id, module_key, record_type, record_id);
CREATE INDEX ix_approval_requester ON approval_request (org_id, requester_id);

CREATE TABLE approval_step (
    id           UUID         PRIMARY KEY,
    request_id   UUID         NOT NULL REFERENCES approval_request (id) ON DELETE CASCADE,
    step_order   SMALLINT     NOT NULL,
    approver_id  UUID         REFERENCES app_user (id),
    approver_role VARCHAR(10),
    status       VARCHAR(10)  NOT NULL CHECK (status IN ('WAITING', 'PENDING', 'APPROVED', 'REJECTED', 'RETURNED')),
    decided_by   UUID         REFERENCES app_user (id),
    decided_at   TIMESTAMPTZ,
    UNIQUE (request_id, step_order)
);
CREATE INDEX ix_approval_step_approver ON approval_step (approver_id, status);

CREATE TABLE approval_action (
    id          UUID         PRIMARY KEY,
    request_id  UUID         NOT NULL REFERENCES approval_request (id) ON DELETE CASCADE,
    actor_id    UUID         NOT NULL REFERENCES app_user (id),
    action      VARCHAR(16)  NOT NULL CHECK (action IN ('SUBMIT', 'APPROVE', 'REJECT', 'REQUEST_CHANGES', 'RESUBMIT', 'WITHDRAW')),
    step_order  SMALLINT,
    comment     TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_approval_action_request ON approval_action (request_id, created_at);

-- ── Calendar ───────────────────────────────────────────────────────────────────────
CREATE TABLE calendar_event (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    title       VARCHAR(200)  NOT NULL,
    description TEXT,
    location    VARCHAR(200),
    starts_at   TIMESTAMPTZ   NOT NULL,
    ends_at     TIMESTAMPTZ   NOT NULL,
    all_day     BOOLEAN       NOT NULL DEFAULT FALSE,
    project_id  UUID          REFERENCES project (id) ON DELETE SET NULL,
    owner_id    UUID          NOT NULL REFERENCES app_user (id),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (ends_at >= starts_at)
);
CREATE INDEX ix_calendar_event_range ON calendar_event (org_id, starts_at);

CREATE TABLE calendar_attendee (
    event_id  UUID  NOT NULL REFERENCES calendar_event (id) ON DELETE CASCADE,
    user_id   UUID  NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, user_id)
);

-- ── Activity and notifications ─────────────────────────────────────────────────────
CREATE TABLE activity (
    id           UUID          PRIMARY KEY,
    org_id       UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    module_key   VARCHAR(20)   NOT NULL,
    record_type  VARCHAR(30)   NOT NULL,
    record_id    UUID          NOT NULL,
    actor_id     UUID          REFERENCES app_user (id),
    verb         VARCHAR(40)   NOT NULL,
    data         JSONB         NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_activity_record ON activity (org_id, record_type, record_id, created_at DESC);
CREATE INDEX ix_activity_org ON activity (org_id, created_at DESC);

CREATE TABLE notification (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    type        VARCHAR(40)   NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    body        VARCHAR(500),
    link        VARCHAR(300),
    actor_id    UUID          REFERENCES app_user (id),
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_notification_user ON notification (user_id, created_at DESC);
