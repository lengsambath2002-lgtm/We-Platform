-- SaaS plans and subscriptions. A plan sets the price per member, how many members (guests don't count), how much file
-- storage and which modules a company may use; each organisation has one subscription. New organisations start a
-- trial of Standard and drop to Free when it ends unless they choose a plan. Organisations that existed before plans
-- keep everything: complimentary Premium.

CREATE TABLE plan (
    code                 VARCHAR(20)    PRIMARY KEY,
    name_en              VARCHAR(60)    NOT NULL,
    name_km              VARCHAR(60)    NOT NULL,
    price_monthly        NUMERIC(10,2)  NOT NULL DEFAULT 0 CHECK (price_monthly >= 0),
    price_yearly         NUMERIC(10,2)  NOT NULL DEFAULT 0 CHECK (price_yearly >= 0),
    max_members          INT            CHECK (max_members IS NULL OR max_members >= 1),
    storage_gb           INT            NOT NULL DEFAULT 1 CHECK (storage_gb >= 0),
    modules              JSONB          NOT NULL DEFAULT '[]',
    trial_days           SMALLINT       NOT NULL DEFAULT 0 CHECK (trial_days BETWEEN 0 AND 90),
    public               BOOLEAN        NOT NULL DEFAULT TRUE,
    sort_order           SMALLINT       NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now()
);

INSERT INTO plan (code, name_en, name_km, price_monthly, price_yearly, max_members, storage_gb, modules, trial_days, sort_order) VALUES
    ('FREE',     'Free',     'ឥតគិតថ្លៃ', 0, 0, 5, 1, '["projects", "business"]', 0, 10),
    ('STANDARD', 'Standard', 'ស្តង់ដារ', 3, 30, NULL, 20, '["projects", "business", "education", "hr", "retail", "service"]', 14, 20),
    ('PREMIUM',  'Premium',  'ពិសេស',    6, 60, NULL, 200, '["projects", "business", "education", "hr", "retail", "service"]', 14, 30);

-- status: TRIALING (until trial_ends_at), ACTIVE (paid until current_period_end), PAST_DUE (payment late),
-- CANCELLED (back to Free), COMPLIMENTARY (granted by the platform, no charge). Prices are per member per month/year.
CREATE TABLE subscription (
    org_id                UUID          PRIMARY KEY REFERENCES organisation (id) ON DELETE CASCADE,
    plan_code             VARCHAR(20)   NOT NULL REFERENCES plan (code),
    status                VARCHAR(14)   NOT NULL CHECK (status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'COMPLIMENTARY')),
    billing_cycle         VARCHAR(8)    NOT NULL DEFAULT 'MONTHLY' CHECK (billing_cycle IN ('MONTHLY', 'YEARLY')),
    trial_ends_at         TIMESTAMPTZ,
    current_period_end    TIMESTAMPTZ,
    cancel_at_period_end  BOOLEAN       NOT NULL DEFAULT FALSE,
    note                  VARCHAR(500),
    updated_by            UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now()
);

INSERT INTO subscription (org_id, plan_code, status, note)
SELECT id, 'PREMIUM', 'COMPLIMENTARY', 'Existing organisation when plans were introduced' FROM organisation;
