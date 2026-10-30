-- HR & Workforce: recruitment and onboarding. Job openings, candidates moving through stages, interviews with a
-- panel's feedback, and an onboarding checklist for each hire. Every table is org-scoped.

CREATE TABLE job_opening (
    id                 UUID          PRIMARY KEY,
    org_id             UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    title              VARCHAR(120)  NOT NULL,
    department         VARCHAR(80),
    employment_type    VARCHAR(10)   CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERN')),
    location           VARCHAR(120),
    description        TEXT,
    openings           SMALLINT      NOT NULL DEFAULT 1 CHECK (openings BETWEEN 1 AND 500),
    hiring_manager_id  UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    status             VARCHAR(10)   NOT NULL CHECK (status IN ('OPEN', 'ON_HOLD', 'CLOSED')),
    created_by         UUID          NOT NULL REFERENCES app_user (id),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_job_opening_org ON job_opening (org_id, status);

-- stage: NEW → SCREENING → INTERVIEW → OFFER → HIRED, or REJECTED (with a reason) at any point.
CREATE TABLE candidate (
    id               UUID          PRIMARY KEY,
    org_id           UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    job_id           UUID          NOT NULL REFERENCES job_opening (id) ON DELETE CASCADE,
    name             VARCHAR(120)  NOT NULL,
    email            VARCHAR(254),
    phone            VARCHAR(30),
    source           VARCHAR(10)   NOT NULL CHECK (source IN ('REFERRAL', 'JOB_BOARD', 'WEBSITE', 'WALK_IN', 'AGENCY', 'OTHER')),
    stage            VARCHAR(10)   NOT NULL CHECK (stage IN ('NEW', 'SCREENING', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED')),
    rating           SMALLINT      CHECK (rating BETWEEN 1 AND 5),
    notes            TEXT,
    cv_file_id       UUID          REFERENCES stored_file (id) ON DELETE SET NULL,
    rejected_reason  VARCHAR(500),
    start_date       DATE,
    invitation_id    UUID          REFERENCES invitation (id) ON DELETE SET NULL,
    hired_at         TIMESTAMPTZ,
    created_by       UUID          NOT NULL REFERENCES app_user (id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_candidate_job ON candidate (org_id, job_id, stage);

CREATE TABLE interview (
    id                UUID          PRIMARY KEY,
    org_id            UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    candidate_id      UUID          NOT NULL REFERENCES candidate (id) ON DELETE CASCADE,
    starts_at         TIMESTAMPTZ   NOT NULL,
    duration_minutes  SMALLINT      NOT NULL CHECK (duration_minutes BETWEEN 10 AND 480),
    location          VARCHAR(200),
    created_by        UUID          NOT NULL REFERENCES app_user (id),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_interview_candidate ON interview (candidate_id, starts_at);
CREATE INDEX ix_interview_org ON interview (org_id, starts_at);

-- Who interviews, and what each of them thought.
CREATE TABLE interview_panel (
    interview_id    UUID          NOT NULL REFERENCES interview (id) ON DELETE CASCADE,
    user_id         UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    recommendation  VARCHAR(10)   CHECK (recommendation IN ('STRONG_YES', 'YES', 'NO', 'STRONG_NO')),
    notes           TEXT,
    submitted_at    TIMESTAMPTZ,
    PRIMARY KEY (interview_id, user_id)
);
CREATE INDEX ix_interview_panel_user ON interview_panel (user_id);

-- The organisation's standard onboarding steps, copied onto each hire (due = start date + days).
CREATE TABLE onboarding_template (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    title       VARCHAR(200)  NOT NULL,
    due_days    SMALLINT      NOT NULL DEFAULT 0 CHECK (due_days BETWEEN -60 AND 180),
    sort_order  SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX ix_onboarding_template_org ON onboarding_template (org_id, sort_order);

CREATE TABLE onboarding_item (
    id            UUID          PRIMARY KEY,
    org_id        UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    candidate_id  UUID          NOT NULL REFERENCES candidate (id) ON DELETE CASCADE,
    title         VARCHAR(200)  NOT NULL,
    due_date      DATE,
    done_at       TIMESTAMPTZ,
    done_by       UUID          REFERENCES app_user (id),
    sort_order    SMALLINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_onboarding_item_candidate ON onboarding_item (candidate_id, sort_order);
