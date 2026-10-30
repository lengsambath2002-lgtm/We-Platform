-- General Business: the sales pipeline. A deal starts as a lead (a contact or company not yet a customer) or for
-- an existing customer, and moves Lead → Qualified → Proposal → Negotiation → Won or Lost.

CREATE TABLE biz_deal (
    id              UUID           PRIMARY KEY,
    org_id          UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    title           VARCHAR(200)   NOT NULL,
    customer_id     UUID           REFERENCES biz_customer (id) ON DELETE SET NULL,
    -- The lead's details while it isn't a customer yet.
    company         VARCHAR(200),
    contact_name    VARCHAR(120),
    email           VARCHAR(160),
    phone           VARCHAR(30),
    value           NUMERIC(14,2)  NOT NULL DEFAULT 0 CHECK (value >= 0),
    stage           VARCHAR(12)    NOT NULL CHECK (stage IN ('LEAD', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST')),
    source          VARCHAR(10)    NOT NULL CHECK (source IN ('REFERRAL', 'WEBSITE', 'SOCIAL', 'EVENT', 'COLD_CALL', 'OTHER')),
    expected_close  DATE,
    owner_id        UUID           NOT NULL REFERENCES app_user (id),
    next_step       VARCHAR(300),
    next_step_date  DATE,
    quote_id        UUID           REFERENCES biz_quote (id) ON DELETE SET NULL,
    lost_reason     VARCHAR(500),
    closed_at       TIMESTAMPTZ,
    notes           VARCHAR(4000),
    created_by      UUID           NOT NULL REFERENCES app_user (id),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (customer_id IS NOT NULL OR company IS NOT NULL OR contact_name IS NOT NULL)
);
CREATE INDEX ix_biz_deal_stage ON biz_deal (org_id, stage);
CREATE INDEX ix_biz_deal_owner ON biz_deal (org_id, owner_id, next_step_date);

-- What happened with a deal: calls, meetings, emails and notes.
CREATE TABLE biz_deal_activity (
    id           UUID           PRIMARY KEY,
    org_id       UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    deal_id      UUID           NOT NULL REFERENCES biz_deal (id) ON DELETE CASCADE,
    kind         VARCHAR(10)    NOT NULL CHECK (kind IN ('CALL', 'MEETING', 'EMAIL', 'NOTE')),
    body         VARCHAR(4000)  NOT NULL,
    happened_on  DATE           NOT NULL,
    created_by   UUID           NOT NULL REFERENCES app_user (id),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_biz_deal_activity ON biz_deal_activity (deal_id, happened_on DESC);
