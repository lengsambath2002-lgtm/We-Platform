-- Retail & Commerce: suppliers and purchase orders. Stock itself stays in VirtualBiz (one count); receiving here
-- records what arrived and leads the person to VirtualBiz to put it into stock. Every table is org-scoped.

CREATE TABLE retail_supplier (
    id                  UUID          PRIMARY KEY,
    org_id              UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name                VARCHAR(200)  NOT NULL,
    contact_name        VARCHAR(120),
    email               VARCHAR(160),
    phone               VARCHAR(30),
    address             VARCHAR(500),
    tax_id              VARCHAR(40),
    payment_terms_days  SMALLINT      NOT NULL DEFAULT 30 CHECK (payment_terms_days BETWEEN 0 AND 365),
    notes               VARCHAR(2000),
    archived            BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by          UUID          NOT NULL REFERENCES app_user (id),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_retail_supplier_name ON retail_supplier (org_id, lower(name));

CREATE TABLE retail_sequence (
    org_id      UUID     PRIMARY KEY REFERENCES organisation (id) ON DELETE CASCADE,
    last_value  INTEGER  NOT NULL CHECK (last_value > 0)
);

-- status: DRAFT → ORDERED → PARTIAL (some received) → RECEIVED, or CANCELLED (only before anything arrives).
-- number: PO-0001, taken when ordered. shop_business_id: the linked VirtualBiz shop the goods are for.
CREATE TABLE retail_po (
    id                UUID           PRIMARY KEY,
    org_id            UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    number            VARCHAR(20),
    supplier_id       UUID           NOT NULL REFERENCES retail_supplier (id),
    status            VARCHAR(10)    NOT NULL CHECK (status IN ('DRAFT', 'ORDERED', 'PARTIAL', 'RECEIVED', 'CANCELLED')),
    order_date        DATE,
    expected_date     DATE,
    shop_business_id  UUID,
    notes             VARCHAR(4000),
    total             NUMERIC(14,2)  NOT NULL DEFAULT 0 CHECK (total >= 0),
    paid              NUMERIC(14,2)  NOT NULL DEFAULT 0 CHECK (paid >= 0),
    ordered_at        TIMESTAMPTZ,
    received_at       TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ,
    cancel_reason     VARCHAR(500),
    created_by        UUID           NOT NULL REFERENCES app_user (id),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (org_id, number),
    CHECK (status = 'DRAFT' OR number IS NOT NULL)
);
CREATE INDEX ix_retail_po_org ON retail_po (org_id, status);
CREATE INDEX ix_retail_po_supplier ON retail_po (supplier_id);

-- sku: the product's code in VirtualBiz, so the person finds it when putting the goods into stock.
CREATE TABLE retail_po_line (
    id           UUID           PRIMARY KEY,
    po_id        UUID           NOT NULL REFERENCES retail_po (id) ON DELETE CASCADE,
    sort_order   SMALLINT       NOT NULL,
    description  VARCHAR(300)   NOT NULL,
    sku          VARCHAR(60),
    quantity     NUMERIC(12,3)  NOT NULL CHECK (quantity > 0),
    unit_cost    NUMERIC(12,2)  NOT NULL CHECK (unit_cost >= 0),
    received     NUMERIC(12,3)  NOT NULL DEFAULT 0 CHECK (received >= 0),
    CHECK (received <= quantity)
);
CREATE INDEX ix_retail_po_line ON retail_po_line (po_id, sort_order);

-- One delivery: lines [{"lineId": "…", "quantity": 5}]. stock_recorded: the person confirmed it is in VirtualBiz.
CREATE TABLE retail_po_receipt (
    id              UUID          PRIMARY KEY,
    org_id          UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    po_id           UUID          NOT NULL REFERENCES retail_po (id) ON DELETE CASCADE,
    received_on     DATE          NOT NULL,
    lines           JSONB         NOT NULL,
    note            VARCHAR(500),
    stock_recorded  BOOLEAN       NOT NULL DEFAULT FALSE,
    received_by     UUID          NOT NULL REFERENCES app_user (id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_retail_po_receipt ON retail_po_receipt (po_id, received_on);

CREATE TABLE retail_supplier_payment (
    id           UUID           PRIMARY KEY,
    org_id       UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    po_id        UUID           NOT NULL REFERENCES retail_po (id) ON DELETE CASCADE,
    paid_on      DATE           NOT NULL,
    amount       NUMERIC(14,2)  NOT NULL CHECK (amount > 0),
    method       VARCHAR(16)    NOT NULL CHECK (method IN ('CASH', 'BANK_TRANSFER', 'KHQR', 'CARD', 'OTHER')),
    reference    VARCHAR(120),
    recorded_by  UUID           NOT NULL REFERENCES app_user (id),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_retail_supplier_payment ON retail_supplier_payment (po_id, paid_on);
