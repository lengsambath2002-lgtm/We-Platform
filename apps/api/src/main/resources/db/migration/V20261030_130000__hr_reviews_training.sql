-- HR & Workforce: performance reviews and training. Every table is org-scoped.

-- A review round: HR writes the questions (each rated 1–5 with a comment), then launches it for everyone.
-- questions: [{"text": "Quality of work"}, …]
CREATE TABLE review_cycle (
    id            UUID          PRIMARY KEY,
    org_id        UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name          VARCHAR(120)  NOT NULL,
    period_start  DATE          NOT NULL,
    period_end    DATE          NOT NULL,
    due_date      DATE          NOT NULL,
    questions     JSONB         NOT NULL,
    status        VARCHAR(10)   NOT NULL CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED')),
    created_by    UUID          NOT NULL REFERENCES app_user (id),
    launched_at   TIMESTAMPTZ,
    closed_at     TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, name),
    CHECK (period_end >= period_start)
);

-- One person's review in a cycle. answers: [{"rating": 4, "comment": "…"}, …] in the cycle's question order.
-- status: SELF (waiting for the person) → MANAGER (waiting for the reviewer) → DONE (shared with the person)
-- → ACKNOWLEDGED (the person has read it).
CREATE TABLE review (
    id                    UUID          PRIMARY KEY,
    org_id                UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    cycle_id              UUID          NOT NULL REFERENCES review_cycle (id) ON DELETE CASCADE,
    employee_id           UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    reviewer_id           UUID          NOT NULL REFERENCES app_user (id),
    status                VARCHAR(12)   NOT NULL CHECK (status IN ('SELF', 'MANAGER', 'DONE', 'ACKNOWLEDGED')),
    self_answers          JSONB,
    self_summary          TEXT,
    self_submitted_at     TIMESTAMPTZ,
    manager_answers       JSONB,
    manager_summary       TEXT,
    overall_rating        SMALLINT      CHECK (overall_rating BETWEEN 1 AND 5),
    manager_submitted_at  TIMESTAMPTZ,
    acknowledged_at       TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (cycle_id, employee_id)
);
CREATE INDEX ix_review_employee ON review (org_id, employee_id);
CREATE INDEX ix_review_reviewer ON review (org_id, reviewer_id, status);

-- A course or session people take; capacity null = no limit.
CREATE TABLE training_course (
    id           UUID          PRIMARY KEY,
    org_id       UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    title        VARCHAR(160)  NOT NULL,
    description  TEXT,
    provider     VARCHAR(120),
    location     VARCHAR(200),
    starts_on    DATE,
    ends_on      DATE,
    hours        NUMERIC(5,1)  CHECK (hours IS NULL OR hours > 0),
    capacity     SMALLINT      CHECK (capacity IS NULL OR capacity > 0),
    self_enrol   BOOLEAN       NOT NULL DEFAULT TRUE,
    status       VARCHAR(10)   NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    created_by   UUID          NOT NULL REFERENCES app_user (id),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (ends_on IS NULL OR starts_on IS NULL OR ends_on >= starts_on)
);
CREATE INDEX ix_training_course_org ON training_course (org_id, status, starts_on);

CREATE TABLE training_enrolment (
    id            UUID          PRIMARY KEY,
    org_id        UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    course_id     UUID          NOT NULL REFERENCES training_course (id) ON DELETE CASCADE,
    user_id       UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    status        VARCHAR(10)   NOT NULL CHECK (status IN ('ENROLLED', 'COMPLETED', 'CANCELLED')),
    enrolled_by   UUID          NOT NULL REFERENCES app_user (id),
    completed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (course_id, user_id)
);
CREATE INDEX ix_training_enrolment_user ON training_enrolment (org_id, user_id);
