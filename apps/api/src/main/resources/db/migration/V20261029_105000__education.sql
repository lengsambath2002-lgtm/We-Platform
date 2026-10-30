-- Education: academic structure (years, terms, grade levels, subjects), teachers (organisation members with a
-- teacher profile), students with their parents / guardians, classes with enrolments, courses (subject × class ×
-- teacher), the weekly timetable, student attendance (separate from employee attendance), assessments and scores,
-- fees (items, student invoices, payments) and student withdrawals decided through the approval engine.
-- Money is USD, NUMERIC(12,2); results and totals are computed by the API. Every table is org-scoped.

UPDATE module SET status = 'AVAILABLE' WHERE key = 'education';

-- One row per organisation, created on first use.
CREATE TABLE edu_settings (
    org_id          UUID          PRIMARY KEY REFERENCES organisation (id) ON DELETE CASCADE,
    school_name     VARCHAR(200),
    principal_name  VARCHAR(120),
    address         VARCHAR(500),
    phone           VARCHAR(30),
    pass_mark       NUMERIC(5,2)  NOT NULL DEFAULT 50 CHECK (pass_mark BETWEEN 0 AND 100),
    updated_by      UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Student numbers (STU-0001) and fee invoice numbers (FEE-0001): one atomic upsert each, so numbers never repeat.
CREATE TABLE edu_sequence (
    org_id      UUID         NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('STUDENT', 'INVOICE')),
    last_value  INTEGER      NOT NULL CHECK (last_value > 0),
    PRIMARY KEY (org_id, kind)
);

CREATE TABLE edu_year (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name        VARCHAR(40)   NOT NULL,
    starts_on   DATE          NOT NULL,
    ends_on     DATE          NOT NULL,
    current     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, name),
    CHECK (ends_on > starts_on)
);
-- At most one current year per organisation.
CREATE UNIQUE INDEX ux_edu_year_current ON edu_year (org_id) WHERE current;

CREATE TABLE edu_term (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    year_id     UUID          NOT NULL REFERENCES edu_year (id) ON DELETE CASCADE,
    name        VARCHAR(40)   NOT NULL,
    starts_on   DATE          NOT NULL,
    ends_on     DATE          NOT NULL,
    UNIQUE (year_id, name),
    CHECK (ends_on >= starts_on)
);
CREATE INDEX ix_edu_term_year ON edu_term (year_id, starts_on);

CREATE TABLE edu_grade (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name_en     VARCHAR(60)   NOT NULL,
    name_km     VARCHAR(60),
    position    SMALLINT      NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_edu_grade_name ON edu_grade (org_id, lower(name_en));

CREATE TABLE edu_subject (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    code        VARCHAR(12)   NOT NULL,
    name_en     VARCHAR(80)   NOT NULL,
    name_km     VARCHAR(80),
    active      BOOLEAN       NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_edu_subject_code ON edu_subject (org_id, upper(code));

-- A teacher is an organisation member (not a guest) with this profile; switched off rather than deleted.
CREATE TABLE edu_teacher (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    user_id     UUID          NOT NULL REFERENCES app_user (id),
    phone       VARCHAR(30),
    specialty   VARCHAR(120),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, user_id)
);

-- A parent or guardian (contacts only; they don't sign in).
CREATE TABLE edu_guardian (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name        VARCHAR(120)  NOT NULL,
    phone       VARCHAR(30),
    email       VARCHAR(160),
    occupation  VARCHAR(120),
    address     VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (phone IS NOT NULL OR email IS NOT NULL)
);
CREATE INDEX ix_edu_guardian_name ON edu_guardian (org_id, lower(name));

-- ACTIVE ─► WITHDRAWN (through an approved withdrawal) or GRADUATED (by an admin).
CREATE TABLE edu_student (
    id                 UUID          PRIMARY KEY,
    org_id             UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    number             VARCHAR(20)   NOT NULL,
    name_en            VARCHAR(120)  NOT NULL,
    name_km            VARCHAR(120),
    gender             VARCHAR(6)    NOT NULL CHECK (gender IN ('FEMALE', 'MALE', 'OTHER')),
    date_of_birth      DATE          NOT NULL,
    admitted_on        DATE          NOT NULL,
    address            VARCHAR(500),
    medical_notes      VARCHAR(1000),
    status             VARCHAR(10)   NOT NULL CHECK (status IN ('ACTIVE', 'WITHDRAWN', 'GRADUATED')),
    left_on            DATE,
    leave_reason       VARCHAR(500),
    created_by         UUID          NOT NULL REFERENCES app_user (id),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, number),
    CHECK (date_of_birth < admitted_on),
    CHECK (status = 'ACTIVE' OR left_on IS NOT NULL)
);
CREATE INDEX ix_edu_student_name ON edu_student (org_id, lower(name_en));

CREATE TABLE edu_student_guardian (
    student_id    UUID         NOT NULL REFERENCES edu_student (id) ON DELETE CASCADE,
    guardian_id   UUID         NOT NULL REFERENCES edu_guardian (id) ON DELETE CASCADE,
    org_id        UUID         NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    relationship  VARCHAR(10)  NOT NULL CHECK (relationship IN ('MOTHER', 'FATHER', 'GUARDIAN', 'GRANDPARENT', 'SIBLING', 'OTHER')),
    is_primary    BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (student_id, guardian_id)
);
CREATE INDEX ix_edu_student_guardian_guardian ON edu_student_guardian (guardian_id);
CREATE UNIQUE INDEX ux_edu_student_guardian_primary ON edu_student_guardian (student_id) WHERE is_primary;

-- A section of a grade level for one academic year (e.g. "7A"), with its homeroom teacher.
CREATE TABLE edu_class (
    id                 UUID          PRIMARY KEY,
    org_id             UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    year_id            UUID          NOT NULL REFERENCES edu_year (id),
    grade_id           UUID          NOT NULL REFERENCES edu_grade (id),
    name               VARCHAR(40)   NOT NULL,
    homeroom_teacher_id UUID         REFERENCES edu_teacher (id),
    room               VARCHAR(40),
    capacity           SMALLINT      CHECK (capacity IS NULL OR capacity BETWEEN 1 AND 200),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (year_id, name)
);
CREATE INDEX ix_edu_class_org ON edu_class (org_id, year_id);

-- A student is in at most one class per academic year; left_on is set when they leave (withdrawal).
CREATE TABLE edu_enrollment (
    class_id     UUID         NOT NULL REFERENCES edu_class (id) ON DELETE CASCADE,
    student_id   UUID         NOT NULL REFERENCES edu_student (id) ON DELETE CASCADE,
    org_id       UUID         NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    year_id      UUID         NOT NULL REFERENCES edu_year (id),
    enrolled_on  DATE         NOT NULL,
    left_on      DATE,
    PRIMARY KEY (class_id, student_id),
    UNIQUE (student_id, year_id)
);

-- A subject taught in a class by one teacher.
CREATE TABLE edu_course (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    class_id    UUID          NOT NULL REFERENCES edu_class (id) ON DELETE CASCADE,
    subject_id  UUID          NOT NULL REFERENCES edu_subject (id),
    teacher_id  UUID          NOT NULL REFERENCES edu_teacher (id),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (class_id, subject_id)
);
CREATE INDEX ix_edu_course_teacher ON edu_course (teacher_id);

-- A weekly lesson (weekday 1 = Monday … 6 = Saturday). Double-booking of a class, a teacher or a room is refused by the API.
CREATE TABLE edu_period (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    class_id    UUID          NOT NULL REFERENCES edu_class (id) ON DELETE CASCADE,
    course_id   UUID          NOT NULL REFERENCES edu_course (id) ON DELETE CASCADE,
    weekday     SMALLINT      NOT NULL CHECK (weekday BETWEEN 1 AND 6),
    starts_at   TIME          NOT NULL,
    ends_at     TIME          NOT NULL,
    room        VARCHAR(40),
    CHECK (ends_at > starts_at)
);
CREATE INDEX ix_edu_period_class ON edu_period (class_id, weekday, starts_at);
CREATE INDEX ix_edu_period_course ON edu_period (course_id);

-- Student attendance: one mark per student, class and day.
CREATE TABLE edu_attendance (
    id           UUID          PRIMARY KEY,
    org_id       UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    class_id     UUID          NOT NULL REFERENCES edu_class (id) ON DELETE CASCADE,
    student_id   UUID          NOT NULL REFERENCES edu_student (id) ON DELETE CASCADE,
    day          DATE          NOT NULL,
    status       VARCHAR(8)    NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED')),
    note         VARCHAR(200),
    recorded_by  UUID          NOT NULL REFERENCES app_user (id),
    recorded_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (class_id, student_id, day)
);
CREATE INDEX ix_edu_attendance_day ON edu_attendance (org_id, day);
CREATE INDEX ix_edu_attendance_student ON edu_attendance (student_id, day);

-- An exam, quiz or assignment of a course in a term; weight is its share of the term result.
CREATE TABLE edu_assessment (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    course_id   UUID          NOT NULL REFERENCES edu_course (id) ON DELETE CASCADE,
    term_id     UUID          NOT NULL REFERENCES edu_term (id),
    name        VARCHAR(80)   NOT NULL,
    kind        VARCHAR(10)   NOT NULL CHECK (kind IN ('EXAM', 'QUIZ', 'ASSIGNMENT', 'PROJECT', 'ORAL')),
    held_on     DATE          NOT NULL,
    max_score   NUMERIC(6,2)  NOT NULL CHECK (max_score > 0),
    weight      NUMERIC(5,2)  NOT NULL CHECK (weight > 0 AND weight <= 100),
    created_by  UUID          NOT NULL REFERENCES app_user (id),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_edu_assessment_course ON edu_assessment (course_id, term_id, held_on);
CREATE INDEX ix_edu_assessment_day ON edu_assessment (org_id, held_on);

CREATE TABLE edu_score (
    assessment_id  UUID          NOT NULL REFERENCES edu_assessment (id) ON DELETE CASCADE,
    student_id     UUID          NOT NULL REFERENCES edu_student (id) ON DELETE CASCADE,
    org_id         UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    score          NUMERIC(6,2)  NOT NULL CHECK (score >= 0),
    entered_by     UUID          NOT NULL REFERENCES app_user (id),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (assessment_id, student_id)
);

-- A fee of an academic year, for one grade level or every grade (grade_id NULL).
CREATE TABLE edu_fee_item (
    id          UUID           PRIMARY KEY,
    org_id      UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    year_id     UUID           NOT NULL REFERENCES edu_year (id),
    grade_id    UUID           REFERENCES edu_grade (id),
    name_en     VARCHAR(120)   NOT NULL,
    name_km     VARCHAR(120),
    amount      NUMERIC(12,2)  NOT NULL CHECK (amount > 0),
    due_on      DATE           NOT NULL,
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_edu_fee_item_year ON edu_fee_item (org_id, year_id);
CREATE INDEX ix_edu_fee_item_due ON edu_fee_item (org_id, due_on);

-- A bill to a student (numbered FEE-0001 when created). ISSUED ─payments─► PARTIALLY_PAID ─► PAID; VOID by admins
-- while nothing is paid. Unpaid past due_on = overdue (derived).
CREATE TABLE edu_invoice (
    id            UUID           PRIMARY KEY,
    org_id        UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    number        VARCHAR(20)    NOT NULL,
    student_id    UUID           NOT NULL REFERENCES edu_student (id),
    fee_item_id   UUID           REFERENCES edu_fee_item (id),
    description   VARCHAR(200)   NOT NULL,
    amount        NUMERIC(12,2)  NOT NULL CHECK (amount > 0),
    paid          NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (paid >= 0 AND paid <= amount),
    issued_on     DATE           NOT NULL,
    due_on        DATE           NOT NULL,
    status        VARCHAR(16)    NOT NULL CHECK (status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID', 'VOID')),
    void_reason   VARCHAR(500),
    voided_at     TIMESTAMPTZ,
    created_by    UUID           NOT NULL REFERENCES app_user (id),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (org_id, number),
    CHECK (due_on >= issued_on)
);
CREATE INDEX ix_edu_invoice_student ON edu_invoice (org_id, student_id);
CREATE INDEX ix_edu_invoice_due ON edu_invoice (org_id, due_on) WHERE status IN ('ISSUED', 'PARTIALLY_PAID');
-- A fee item is billed to a student once (void invoices excepted).
CREATE UNIQUE INDEX ux_edu_invoice_item ON edu_invoice (fee_item_id, student_id) WHERE fee_item_id IS NOT NULL AND status <> 'VOID';

CREATE TABLE edu_payment (
    id            UUID           PRIMARY KEY,
    org_id        UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    invoice_id    UUID           NOT NULL REFERENCES edu_invoice (id) ON DELETE CASCADE,
    paid_on       DATE           NOT NULL,
    amount        NUMERIC(12,2)  NOT NULL CHECK (amount > 0),
    method        VARCHAR(16)    NOT NULL CHECK (method IN ('CASH', 'BANK_TRANSFER', 'KHQR', 'CARD', 'OTHER')),
    reference     VARCHAR(120),
    recorded_by   UUID           NOT NULL REFERENCES app_user (id),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_edu_payment_invoice ON edu_payment (invoice_id);
CREATE INDEX ix_edu_payment_day ON edu_payment (org_id, paid_on);

-- A request to withdraw a student, decided through the approval engine (default "Student withdrawal": an Admin).
-- PENDING ─► APPROVED (the student becomes WITHDRAWN) | REJECTED | CANCELLED (the requester withdrew it).
CREATE TABLE edu_withdrawal (
    id            UUID          PRIMARY KEY,
    org_id        UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    student_id    UUID          NOT NULL REFERENCES edu_student (id) ON DELETE CASCADE,
    effective_on  DATE          NOT NULL,
    reason        VARCHAR(500)  NOT NULL,
    status        VARCHAR(10)   NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    approval_id   UUID          REFERENCES approval_request (id) ON DELETE SET NULL,
    requested_by  UUID          NOT NULL REFERENCES app_user (id),
    decided_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_edu_withdrawal_student ON edu_withdrawal (org_id, student_id);
-- One open request per student.
CREATE UNIQUE INDEX ux_edu_withdrawal_open ON edu_withdrawal (student_id) WHERE status = 'PENDING';
