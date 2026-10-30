-- Employee records: personal details on the profile (private to the person and HR), employment contracts, employee
-- documents with expiry dates, and salary history (every change of monthly salary, from now on).

ALTER TABLE employee_profile ADD COLUMN nickname VARCHAR(40);
ALTER TABLE employee_profile ADD COLUMN duty VARCHAR(120);
ALTER TABLE employee_profile ADD COLUMN category VARCHAR(60);
ALTER TABLE employee_profile ADD COLUMN gender VARCHAR(6) CHECK (gender IN ('MALE', 'FEMALE', 'OTHER'));
ALTER TABLE employee_profile ADD COLUMN date_of_birth DATE;
ALTER TABLE employee_profile ADD COLUMN nationality VARCHAR(60);
ALTER TABLE employee_profile ADD COLUMN id_type VARCHAR(12) CHECK (id_type IN ('NATIONAL_ID', 'PASSPORT', 'OTHER'));
ALTER TABLE employee_profile ADD COLUMN id_number VARCHAR(40);
ALTER TABLE employee_profile ADD COLUMN id_expires_on DATE;
ALTER TABLE employee_profile ADD COLUMN nssf_number VARCHAR(30);
ALTER TABLE employee_profile ADD COLUMN home_address VARCHAR(300);
ALTER TABLE employee_profile ADD COLUMN personal_email VARCHAR(254);
ALTER TABLE employee_profile ADD COLUMN personal_phone VARCHAR(30);

-- PROBATION, FIXED_TERM (Cambodia: FDC, has an end) or UNLIMITED (UDC), INTERNSHIP, OTHER.
CREATE TABLE employee_contract (
    id                  UUID           PRIMARY KEY,
    org_id              UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    user_id             UUID           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind                VARCHAR(12)    NOT NULL CHECK (kind IN ('PROBATION', 'FIXED_TERM', 'UNLIMITED', 'INTERNSHIP', 'OTHER')),
    starts_on           DATE           NOT NULL,
    ends_on             DATE,
    probation_ends_on   DATE,
    monthly_salary      NUMERIC(12,2)  CHECK (monthly_salary >= 0),
    note                VARCHAR(1000),
    file_id             UUID           REFERENCES stored_file (id) ON DELETE SET NULL,
    created_by          UUID           REFERENCES app_user (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (ends_on IS NULL OR ends_on >= starts_on)
);
CREATE INDEX employee_contract_person ON employee_contract (org_id, user_id, starts_on);

CREATE TABLE employee_document (
    id           UUID           PRIMARY KEY,
    org_id       UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    user_id      UUID           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    category     VARCHAR(12)    NOT NULL CHECK (category IN ('ID', 'CONTRACT', 'CERTIFICATE', 'MEDICAL', 'OTHER')),
    title        VARCHAR(160)   NOT NULL,
    expires_on   DATE,
    file_id      UUID           NOT NULL REFERENCES stored_file (id),
    uploaded_by  UUID           REFERENCES app_user (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX employee_document_person ON employee_document (org_id, user_id);

CREATE TABLE pay_change (
    id              UUID           PRIMARY KEY,
    org_id          UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    user_id         UUID           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    monthly_salary  NUMERIC(12,2)  NOT NULL CHECK (monthly_salary >= 0),
    previous        NUMERIC(12,2),
    effective_on    DATE           NOT NULL,
    reason          VARCHAR(300),
    changed_by      UUID           REFERENCES app_user (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX pay_change_person ON pay_change (org_id, user_id, effective_on);
-- What is known today becomes the first entry.
INSERT INTO pay_change (id, org_id, user_id, monthly_salary, effective_on, reason, changed_by, created_at)
SELECT gen_random_uuid(), org_id, user_id, monthly_salary, (updated_at AT TIME ZONE 'Asia/Phnom_Penh')::date, NULL, updated_by, updated_at
FROM employee_pay;
