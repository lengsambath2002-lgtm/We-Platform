-- The platform's invoices to organisations: one per paid period (price per member × members), paid by bank transfer or
-- KHQR. The organisation says it paid ("claimed", with the transfer reference); the platform confirms it (PAID).
-- Unpaid after the due date the subscription is PAST_DUE; after the grace period it moves to Free (invoice VOID).
CREATE SEQUENCE billing_invoice_seq;

CREATE TABLE billing_invoice (
    id                 UUID           PRIMARY KEY,
    org_id             UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    number             VARCHAR(20)    NOT NULL UNIQUE,
    plan_code          VARCHAR(20)    NOT NULL,
    billing_cycle      VARCHAR(8)     NOT NULL CHECK (billing_cycle IN ('MONTHLY', 'YEARLY')),
    seats              INT            NOT NULL CHECK (seats >= 0),
    unit_price         NUMERIC(10,2)  NOT NULL,
    amount             NUMERIC(12,2)  NOT NULL CHECK (amount >= 0),
    period_start       TIMESTAMPTZ    NOT NULL,
    period_end         TIMESTAMPTZ    NOT NULL,
    status             VARCHAR(6)     NOT NULL CHECK (status IN ('OPEN', 'PAID', 'VOID')),
    issued_at          TIMESTAMPTZ    NOT NULL,
    due_at             TIMESTAMPTZ    NOT NULL,
    khqr               VARCHAR(400),
    claimed_at         TIMESTAMPTZ,
    claim_reference    VARCHAR(120),
    claimed_by         UUID           REFERENCES app_user (id) ON DELETE SET NULL,
    paid_at            TIMESTAMPTZ,
    payment_method     VARCHAR(14)    CHECK (payment_method IN ('BANK_TRANSFER', 'KHQR', 'ABA_PAYWAY', 'CASH', 'OTHER')),
    payment_reference  VARCHAR(120),
    recorded_by        UUID           REFERENCES app_user (id) ON DELETE SET NULL,
    void_reason        VARCHAR(300),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX billing_invoice_org ON billing_invoice (org_id, issued_at DESC);
CREATE INDEX billing_invoice_open ON billing_invoice (status, due_at);
