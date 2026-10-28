-- HR & Workforce, first industry module: employee profiles, leave types, balance adjustments and
-- leave requests (decided through the approval engine). Every table is org-scoped.

UPDATE module SET status = 'AVAILABLE' WHERE key = 'hr';

-- Approval steps may name "the requester's manager"; the owning module resolves who that is.
ALTER TABLE workflow_step_def DROP CONSTRAINT workflow_step_def_approver_type_check;
ALTER TABLE workflow_step_def ADD CONSTRAINT workflow_step_def_approver_type_check
    CHECK (approver_type IN ('USER', 'ORG_ROLE', 'PROJECT_LEAD', 'MANAGER'));

-- One per person in the organisation, created on first use. The job title stays on membership.title.
CREATE TABLE employee_profile (
    id                  UUID          PRIMARY KEY,
    org_id              UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    membership_id       UUID          NOT NULL UNIQUE REFERENCES membership (id) ON DELETE CASCADE,
    user_id             UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    department          VARCHAR(80),
    manager_id          UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    employment_type     VARCHAR(10)   CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERN')),
    start_date          DATE,
    work_phone          VARCHAR(30),
    emergency_name      VARCHAR(80),
    emergency_phone     VARCHAR(30),
    emergency_relation  VARCHAR(40),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, user_id),
    CHECK (manager_id IS NULL OR manager_id <> user_id)
);
CREATE INDEX ix_employee_profile_manager ON employee_profile (org_id, manager_id);

-- allowance_days NULL = unlimited.
CREATE TABLE leave_type (
    id              UUID          PRIMARY KEY,
    org_id          UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name_en         VARCHAR(60)   NOT NULL,
    name_km         VARCHAR(60)   NOT NULL,
    paid            BOOLEAN       NOT NULL,
    allowance_days  NUMERIC(5,1)  CHECK (allowance_days IS NULL OR allowance_days BETWEEN 0 AND 366),
    half_days       BOOLEAN       NOT NULL DEFAULT TRUE,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order      SMALLINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, name_en)
);

-- An HR manager's change to one person's allowance for one type and year (+ adds days, - removes).
CREATE TABLE leave_adjustment (
    id             UUID          PRIMARY KEY,
    org_id         UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    user_id        UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    leave_type_id  UUID          NOT NULL REFERENCES leave_type (id) ON DELETE CASCADE,
    year           SMALLINT      NOT NULL CHECK (year BETWEEN 2000 AND 2100),
    days           NUMERIC(5,1)  NOT NULL CHECK (days <> 0),
    reason         VARCHAR(500)  NOT NULL,
    created_by     UUID          NOT NULL REFERENCES app_user (id),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_leave_adjustment_person ON leave_adjustment (org_id, user_id, year);

-- half_start: the first day starts at midday; half_end: the last day ends at midday.
CREATE TABLE leave_request (
    id             UUID          PRIMARY KEY,
    org_id         UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    user_id        UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    leave_type_id  UUID          NOT NULL REFERENCES leave_type (id),
    start_date     DATE          NOT NULL,
    end_date       DATE          NOT NULL,
    half_start     BOOLEAN       NOT NULL DEFAULT FALSE,
    half_end       BOOLEAN       NOT NULL DEFAULT FALSE,
    days           NUMERIC(5,1)  NOT NULL CHECK (days > 0),
    reason         TEXT,
    status         VARCHAR(10)   NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLED')),
    approval_id    UUID          REFERENCES approval_request (id) ON DELETE SET NULL,
    decided_at     TIMESTAMPTZ,
    cancelled_by   UUID          REFERENCES app_user (id),
    cancel_reason  VARCHAR(500),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date)
);
CREATE INDEX ix_leave_request_person ON leave_request (org_id, user_id, start_date);
CREATE INDEX ix_leave_request_range ON leave_request (org_id, start_date, end_date) WHERE status IN ('PENDING', 'APPROVED');
