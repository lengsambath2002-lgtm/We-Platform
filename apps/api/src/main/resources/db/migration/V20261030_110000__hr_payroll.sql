-- HR & Workforce: payroll. Salaries are in USD; Cambodia's Tax on Salary and NSSF are worked out in riel at the
-- pay month's exchange rate. Every table is org-scoped. HR managers only, except each person's own payslips.

-- The organisation's statutory settings. The defaults are the rates in force when this was written; HR edits
-- them when the law changes (an accountant should confirm them before real use).
-- tax_brackets: [{"upToKhr": 1500000, "ratePct": 0}, …, {"upToKhr": null, "ratePct": 20}] — monthly, progressive.
CREATE TABLE payroll_settings (
    org_id                     UUID           PRIMARY KEY REFERENCES organisation (id) ON DELETE CASCADE,
    tax_brackets               JSONB          NOT NULL,
    dependant_allowance_khr    INTEGER        NOT NULL CHECK (dependant_allowance_khr >= 0),
    nssf_floor_khr             INTEGER        NOT NULL CHECK (nssf_floor_khr >= 0),
    nssf_cap_khr               INTEGER        NOT NULL CHECK (nssf_cap_khr >= 0),
    nssf_pension_employee_pct  NUMERIC(5,2)   NOT NULL CHECK (nssf_pension_employee_pct BETWEEN 0 AND 100),
    nssf_pension_employer_pct  NUMERIC(5,2)   NOT NULL CHECK (nssf_pension_employer_pct BETWEEN 0 AND 100),
    nssf_health_employer_pct   NUMERIC(5,2)   NOT NULL CHECK (nssf_health_employer_pct BETWEEN 0 AND 100),
    nssf_risk_employer_pct     NUMERIC(5,2)   NOT NULL CHECK (nssf_risk_employer_pct BETWEEN 0 AND 100),
    overtime_rate_pct          NUMERIC(5,1)   NOT NULL CHECK (overtime_rate_pct BETWEEN 100 AND 400),
    updated_by                 UUID           REFERENCES app_user (id),
    updated_at                 TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- What one person is paid. dependants: spouse and children who lower Tax on Salary.
CREATE TABLE employee_pay (
    id                UUID            PRIMARY KEY,
    org_id            UUID            NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    user_id           UUID            NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    monthly_salary    NUMERIC(12,2)   NOT NULL CHECK (monthly_salary >= 0),
    dependants        SMALLINT        NOT NULL DEFAULT 0 CHECK (dependants BETWEEN 0 AND 20),
    nssf_enrolled     BOOLEAN         NOT NULL DEFAULT TRUE,
    bank_name         VARCHAR(60),
    bank_account      VARCHAR(40),
    updated_by        UUID            REFERENCES app_user (id),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (org_id, user_id)
);

-- One month's payroll: DRAFT (being prepared, recalculated freely) → APPROVED (locked) → PAID (people see their
-- payslips and are emailed). exchange_rate: riel per US dollar for the month's tax.
CREATE TABLE pay_run (
    id             UUID           PRIMARY KEY,
    org_id         UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    month          VARCHAR(7)     NOT NULL CHECK (month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    exchange_rate  NUMERIC(8,2)   NOT NULL CHECK (exchange_rate > 0),
    status         VARCHAR(10)    NOT NULL CHECK (status IN ('DRAFT', 'APPROVED', 'PAID')),
    note           VARCHAR(500),
    created_by     UUID           NOT NULL REFERENCES app_user (id),
    approved_by    UUID           REFERENCES app_user (id),
    approved_at    TIMESTAMPTZ,
    paid_by        UUID           REFERENCES app_user (id),
    paid_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (org_id, month)
);

-- One person's pay for a run, with every figure kept as calculated (so a payslip never changes after approval).
-- adjustments: HR's extra lines [{"label": "Transport", "amount": 20.00}] (negative = a deduction).
CREATE TABLE payslip (
    id                    UUID            PRIMARY KEY,
    org_id                UUID            NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    pay_run_id            UUID            NOT NULL REFERENCES pay_run (id) ON DELETE CASCADE,
    user_id               UUID            NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    monthly_salary        NUMERIC(12,2)   NOT NULL,
    working_days          SMALLINT        NOT NULL,
    unpaid_leave_days     NUMERIC(5,1)    NOT NULL DEFAULT 0,
    unpaid_leave_amount   NUMERIC(12,2)   NOT NULL DEFAULT 0,
    overtime_minutes      INTEGER         NOT NULL DEFAULT 0,
    overtime_amount       NUMERIC(12,2)   NOT NULL DEFAULT 0,
    adjustments           JSONB           NOT NULL DEFAULT '[]'::jsonb,
    gross                 NUMERIC(12,2)   NOT NULL,
    dependants            SMALLINT        NOT NULL,
    taxable_khr           BIGINT          NOT NULL,
    tax_khr               BIGINT          NOT NULL,
    tax                   NUMERIC(12,2)   NOT NULL,
    nssf_employee         NUMERIC(12,2)   NOT NULL,
    nssf_employer         NUMERIC(12,2)   NOT NULL,
    net                   NUMERIC(12,2)   NOT NULL,
    bank_name             VARCHAR(60),
    bank_account          VARCHAR(40),
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (pay_run_id, user_id)
);
CREATE INDEX ix_payslip_person ON payslip (org_id, user_id);
