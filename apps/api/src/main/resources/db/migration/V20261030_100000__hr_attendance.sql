-- HR & Workforce: work schedules, attendance (clock in / out) and overtime requests. Every table is org-scoped.

-- A shift pattern: when the day starts and ends, its unpaid break, which weekdays are worked
-- (ISO 1 = Monday … 7 = Sunday) and how many minutes after the start still count as on time.
-- One per organisation is the default for people who have none of their own.
CREATE TABLE work_schedule (
    id             UUID          PRIMARY KEY,
    org_id         UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name           VARCHAR(60)   NOT NULL,
    start_time     TIME          NOT NULL,
    end_time       TIME          NOT NULL,
    break_minutes  SMALLINT      NOT NULL DEFAULT 60 CHECK (break_minutes BETWEEN 0 AND 240),
    grace_minutes  SMALLINT      NOT NULL DEFAULT 10 CHECK (grace_minutes BETWEEN 0 AND 120),
    work_days      VARCHAR(13)   NOT NULL CHECK (work_days ~ '^[1-7](,[1-7]){0,6}$'),
    is_default     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, name),
    CHECK (end_time > start_time)
);
CREATE UNIQUE INDEX ux_work_schedule_default ON work_schedule (org_id) WHERE is_default;

ALTER TABLE employee_profile ADD COLUMN schedule_id UUID REFERENCES work_schedule (id) ON DELETE SET NULL;

-- One row per person and working day. clock_out may be missing (still at work, or forgot).
-- An HR correction keeps who changed it and why (edited_by, edit_reason).
CREATE TABLE attendance_record (
    id            UUID          PRIMARY KEY,
    org_id        UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    user_id       UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    work_date     DATE          NOT NULL,
    clock_in      TIMESTAMPTZ   NOT NULL,
    clock_out     TIMESTAMPTZ,
    note          VARCHAR(300),
    edited_by     UUID          REFERENCES app_user (id),
    edit_reason   VARCHAR(500),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, user_id, work_date),
    CHECK (clock_out IS NULL OR clock_out > clock_in)
);
CREATE INDEX ix_attendance_day ON attendance_record (org_id, work_date);

-- Extra hours worked on a day, asked for afterwards and decided through the approval engine.
CREATE TABLE overtime_request (
    id           UUID          PRIMARY KEY,
    org_id       UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    user_id      UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    work_date    DATE          NOT NULL,
    minutes      SMALLINT      NOT NULL CHECK (minutes BETWEEN 15 AND 720),
    reason       VARCHAR(1000) NOT NULL,
    status       VARCHAR(10)   NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    approval_id  UUID          REFERENCES approval_request (id) ON DELETE SET NULL,
    decided_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_overtime_person ON overtime_request (org_id, user_id, work_date);
