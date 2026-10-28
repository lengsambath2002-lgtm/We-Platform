-- General Business: customers, products & services, quotes, invoices with payments, expenses (decided
-- through the approval engine) and the organisation's business settings. Money is USD, NUMERIC(12,2);
-- totals are computed by the API. Every table is org-scoped.

UPDATE module SET status = 'AVAILABLE' WHERE key = 'business';

-- One row per organisation, created on first use.
CREATE TABLE biz_settings (
    org_id              UUID          PRIMARY KEY REFERENCES organisation (id) ON DELETE CASCADE,
    legal_name          VARCHAR(200),
    address             VARCHAR(500),
    tax_id              VARCHAR(40),
    phone               VARCHAR(30),
    email               VARCHAR(160),
    invoice_footer      VARCHAR(2000),
    payment_terms_days  SMALLINT      NOT NULL DEFAULT 30 CHECK (payment_terms_days BETWEEN 0 AND 365),
    vat_default         BOOLEAN       NOT NULL DEFAULT TRUE,
    updated_by          UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Document numbers (kind QUOTE or INVOICE): taken with one atomic upsert, so two at once never get the same.
CREATE TABLE biz_sequence (
    org_id      UUID         NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('QUOTE', 'INVOICE')),
    last_value  INTEGER      NOT NULL CHECK (last_value > 0),
    PRIMARY KEY (org_id, kind)
);

CREATE TABLE biz_customer (
    id            UUID          PRIMARY KEY,
    org_id        UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    kind          VARCHAR(10)   NOT NULL CHECK (kind IN ('COMPANY', 'INDIVIDUAL')),
    name          VARCHAR(200)  NOT NULL,
    contact_name  VARCHAR(120),
    email         VARCHAR(160),
    phone         VARCHAR(30),
    address       VARCHAR(500),
    tax_id        VARCHAR(40),
    notes         VARCHAR(2000),
    archived      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by    UUID          NOT NULL REFERENCES app_user (id),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_biz_customer_name ON biz_customer (org_id, lower(name));

CREATE TABLE biz_product (
    id          UUID           PRIMARY KEY,
    org_id      UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name_en     VARCHAR(160)   NOT NULL,
    name_km     VARCHAR(160),
    unit        VARCHAR(20)    NOT NULL,
    unit_price  NUMERIC(12,2)  NOT NULL CHECK (unit_price >= 0),
    vat         BOOLEAN        NOT NULL DEFAULT TRUE,
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_biz_product_name ON biz_product (org_id, lower(name_en));

-- number: Q-0001, taken at creation. valid_until passed while SENT = expired (derived, not stored).
CREATE TABLE biz_quote (
    id              UUID           PRIMARY KEY,
    org_id          UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    number          VARCHAR(20)    NOT NULL,
    customer_id     UUID           NOT NULL REFERENCES biz_customer (id),
    status          VARCHAR(10)    NOT NULL CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'DECLINED')),
    issue_date      DATE           NOT NULL,
    valid_until     DATE,
    vat_enabled     BOOLEAN        NOT NULL,
    vat_rate        NUMERIC(5,2)   NOT NULL,
    subtotal        NUMERIC(12,2)  NOT NULL,
    discount_total  NUMERIC(12,2)  NOT NULL,
    vat_total       NUMERIC(12,2)  NOT NULL,
    total           NUMERIC(12,2)  NOT NULL,
    notes           VARCHAR(4000),
    invoice_id      UUID,
    created_by      UUID           NOT NULL REFERENCES app_user (id),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (org_id, number),
    CHECK (valid_until IS NULL OR valid_until >= issue_date)
);
CREATE INDEX ix_biz_quote_customer ON biz_quote (org_id, customer_id);

-- number: INV-0001, taken when issued (drafts have none). due_date passed while unpaid = overdue (derived).
CREATE TABLE biz_invoice (
    id              UUID           PRIMARY KEY,
    org_id          UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    number          VARCHAR(20),
    customer_id     UUID           NOT NULL REFERENCES biz_customer (id),
    status          VARCHAR(16)    NOT NULL CHECK (status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'VOID')),
    issue_date      DATE,
    due_date        DATE,
    vat_enabled     BOOLEAN        NOT NULL,
    vat_rate        NUMERIC(5,2)   NOT NULL,
    subtotal        NUMERIC(12,2)  NOT NULL,
    discount_total  NUMERIC(12,2)  NOT NULL,
    vat_total       NUMERIC(12,2)  NOT NULL,
    total           NUMERIC(12,2)  NOT NULL,
    paid            NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (paid >= 0 AND paid <= total),
    notes           VARCHAR(4000),
    quote_id        UUID           REFERENCES biz_quote (id) ON DELETE SET NULL,
    issued_at       TIMESTAMPTZ,
    issued_by       UUID           REFERENCES app_user (id),
    void_reason     VARCHAR(500),
    voided_at       TIMESTAMPTZ,
    voided_by       UUID           REFERENCES app_user (id),
    created_by      UUID           NOT NULL REFERENCES app_user (id),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (org_id, number),
    CHECK (status = 'DRAFT' OR (number IS NOT NULL AND issue_date IS NOT NULL AND due_date IS NOT NULL)),
    CHECK (due_date IS NULL OR issue_date IS NULL OR due_date >= issue_date)
);
CREATE INDEX ix_biz_invoice_customer ON biz_invoice (org_id, customer_id);
CREATE INDEX ix_biz_invoice_due ON biz_invoice (org_id, due_date) WHERE status IN ('ISSUED', 'PARTIALLY_PAID');
ALTER TABLE biz_quote ADD CONSTRAINT fk_biz_quote_invoice FOREIGN KEY (invoice_id) REFERENCES biz_invoice (id) ON DELETE SET NULL;

-- A line of a quote or an invoice (exactly one). net = qty × price − discount, rounded half-up to cents.
CREATE TABLE biz_line (
    id            UUID           PRIMARY KEY,
    org_id        UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    quote_id      UUID           REFERENCES biz_quote (id) ON DELETE CASCADE,
    invoice_id    UUID           REFERENCES biz_invoice (id) ON DELETE CASCADE,
    position      SMALLINT       NOT NULL,
    product_id    UUID           REFERENCES biz_product (id) ON DELETE SET NULL,
    description   VARCHAR(500)   NOT NULL,
    unit          VARCHAR(20),
    quantity      NUMERIC(12,3)  NOT NULL CHECK (quantity > 0),
    unit_price    NUMERIC(12,2)  NOT NULL CHECK (unit_price >= 0),
    discount_pct  NUMERIC(5,2)   NOT NULL DEFAULT 0 CHECK (discount_pct BETWEEN 0 AND 100),
    vat           BOOLEAN        NOT NULL,
    discount      NUMERIC(12,2)  NOT NULL,
    net           NUMERIC(12,2)  NOT NULL,
    CHECK ((quote_id IS NULL) <> (invoice_id IS NULL))
);
CREATE INDEX ix_biz_line_quote ON biz_line (quote_id) WHERE quote_id IS NOT NULL;
CREATE INDEX ix_biz_line_invoice ON biz_line (invoice_id) WHERE invoice_id IS NOT NULL;

CREATE TABLE biz_payment (
    id            UUID           PRIMARY KEY,
    org_id        UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    invoice_id    UUID           NOT NULL REFERENCES biz_invoice (id) ON DELETE CASCADE,
    paid_on       DATE           NOT NULL,
    amount        NUMERIC(12,2)  NOT NULL CHECK (amount > 0),
    method        VARCHAR(16)    NOT NULL CHECK (method IN ('CASH', 'BANK_TRANSFER', 'KHQR', 'CARD', 'OTHER')),
    reference     VARCHAR(120),
    recorded_by   UUID           NOT NULL REFERENCES app_user (id),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_biz_payment_invoice ON biz_payment (invoice_id);

CREATE TABLE biz_expense_category (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name_en     VARCHAR(60)   NOT NULL,
    name_km     VARCHAR(60)   NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order  SMALLINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, name_en)
);

-- DRAFT ─submit─► PENDING ─approved─► APPROVED ─paid─► PAID; PENDING ─► REJECTED, or back to DRAFT when withdrawn.
-- amount includes vat_amount (the VAT part, if the receipt shows one).
CREATE TABLE biz_expense (
    id               UUID           PRIMARY KEY,
    org_id           UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    user_id          UUID           NOT NULL REFERENCES app_user (id),
    spent_on         DATE           NOT NULL,
    category_id      UUID           NOT NULL REFERENCES biz_expense_category (id),
    supplier         VARCHAR(160),
    amount           NUMERIC(12,2)  NOT NULL CHECK (amount > 0),
    vat_amount       NUMERIC(12,2)  CHECK (vat_amount IS NULL OR (vat_amount >= 0 AND vat_amount <= amount)),
    notes            VARCHAR(2000),
    receipt_file_id  UUID           REFERENCES stored_file (id) ON DELETE SET NULL,
    status           VARCHAR(10)    NOT NULL CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'PAID')),
    approval_id      UUID           REFERENCES approval_request (id) ON DELETE SET NULL,
    decided_at       TIMESTAMPTZ,
    paid_at          TIMESTAMPTZ,
    paid_by          UUID           REFERENCES app_user (id),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_biz_expense_person ON biz_expense (org_id, user_id, spent_on);
CREATE INDEX ix_biz_expense_status ON biz_expense (org_id, status, spent_on);
