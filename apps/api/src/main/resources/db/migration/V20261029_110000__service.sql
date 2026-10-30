-- Service & Operations: service staff (dispatchers, technicians), customers and their sites, assets, tickets with
-- SLA targets, work orders (technicians, schedule, checklist, parts and labour, approval above a cost threshold,
-- completion with sign-off) and preventive maintenance plans. Bookings stay in VirtualBiz (linked, not copied).
-- Money is USD, NUMERIC(12,2), computed by the API. Every table is org-scoped.

UPDATE module SET status = 'AVAILABLE' WHERE key = 'service';

-- One row per organisation, created on first use. approval_threshold: work orders whose total is above it need
-- approval before they are scheduled (NULL = never). labour_rate: the starting price of a labour hour.
CREATE TABLE svc_settings (
    org_id              UUID           PRIMARY KEY REFERENCES organisation (id) ON DELETE CASCADE,
    approval_threshold  NUMERIC(12,2)  DEFAULT 500.00 CHECK (approval_threshold IS NULL OR approval_threshold >= 0),
    labour_rate         NUMERIC(12,2)  NOT NULL DEFAULT 15.00 CHECK (labour_rate >= 0),
    updated_by          UUID           REFERENCES app_user (id) ON DELETE SET NULL,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- Ticket and work order numbers (TCK-0001, WO-0001): one atomic upsert, so two at once never get the same.
CREATE TABLE svc_sequence (
    org_id      UUID         NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    kind        VARCHAR(12)  NOT NULL CHECK (kind IN ('TICKET', 'WORK_ORDER')),
    last_value  INTEGER      NOT NULL CHECK (last_value > 0),
    PRIMARY KEY (org_id, kind)
);

-- A member's role in the module. Owners and Admins need no row (they run everything); a member without an active
-- row has no access. Technicians are the people work orders are assigned to.
CREATE TABLE svc_staff (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    user_id     UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role        VARCHAR(12)   NOT NULL CHECK (role IN ('DISPATCHER', 'TECHNICIAN')),
    skills      JSONB         NOT NULL DEFAULT '[]'::jsonb,
    phone       VARCHAR(30),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, user_id)
);

-- Response and resolution targets per ticket priority, in minutes (defaults created on first use).
CREATE TABLE svc_sla_policy (
    org_id              UUID         NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    priority            VARCHAR(8)   NOT NULL CHECK (priority IN ('URGENT', 'HIGH', 'MEDIUM', 'LOW')),
    response_minutes    INTEGER      NOT NULL CHECK (response_minutes > 0),
    resolution_minutes  INTEGER      NOT NULL CHECK (resolution_minutes > 0),
    PRIMARY KEY (org_id, priority),
    CHECK (resolution_minutes >= response_minutes)
);

CREATE TABLE svc_customer (
    id            UUID          PRIMARY KEY,
    org_id        UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name          VARCHAR(200)  NOT NULL,
    contact_name  VARCHAR(120),
    phone         VARCHAR(30),
    email         VARCHAR(160),
    address       VARCHAR(500),
    notes         VARCHAR(2000),
    archived      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by    UUID          NOT NULL REFERENCES app_user (id),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_svc_customer_name ON svc_customer (org_id, lower(name));

-- Where the work happens (a branch, a building, a home).
CREATE TABLE svc_site (
    id           UUID          PRIMARY KEY,
    org_id       UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    customer_id  UUID          NOT NULL REFERENCES svc_customer (id) ON DELETE CASCADE,
    name         VARCHAR(160)  NOT NULL,
    address      VARCHAR(500),
    notes        VARCHAR(1000),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_svc_site_customer ON svc_site (customer_id);

-- Equipment the organisation services. Under warranty while warranty_end >= today (derived).
CREATE TABLE svc_asset (
    id            UUID          PRIMARY KEY,
    org_id        UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    customer_id   UUID          NOT NULL REFERENCES svc_customer (id),
    site_id       UUID          REFERENCES svc_site (id) ON DELETE SET NULL,
    name          VARCHAR(160)  NOT NULL,
    serial        VARCHAR(80),
    model         VARCHAR(120),
    manufacturer  VARCHAR(120),
    installed_on  DATE,
    warranty_end  DATE,
    status        VARCHAR(10)   NOT NULL CHECK (status IN ('ACTIVE', 'IN_REPAIR', 'RETIRED')),
    notes         VARCHAR(2000),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_svc_asset_customer ON svc_asset (org_id, customer_id);
CREATE UNIQUE INDEX ux_svc_asset_serial ON svc_asset (org_id, lower(serial)) WHERE serial IS NOT NULL;

-- NEW → TRIAGED → IN_PROGRESS ⇄ WAITING → RESOLVED → CLOSED; RESOLVED / CLOSED can be reopened (→ IN_PROGRESS).
-- Due and warn times come from the SLA policy of the priority (warn = 75% of the window); breached / at risk are
-- derived from them and responded_at / resolved_at, never stored.
CREATE TABLE svc_ticket (
    id                  UUID          PRIMARY KEY,
    org_id              UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    number              VARCHAR(20)   NOT NULL,
    title               VARCHAR(200)  NOT NULL,
    description         VARCHAR(4000),
    customer_id         UUID          REFERENCES svc_customer (id),
    site_id             UUID          REFERENCES svc_site (id) ON DELETE SET NULL,
    asset_id            UUID          REFERENCES svc_asset (id) ON DELETE SET NULL,
    requester_name      VARCHAR(120),
    requester_phone     VARCHAR(30),
    category            VARCHAR(12)   NOT NULL CHECK (category IN ('REPAIR', 'INSTALLATION', 'MAINTENANCE', 'INSPECTION', 'COMPLAINT', 'OTHER')),
    priority            VARCHAR(8)    NOT NULL CHECK (priority IN ('URGENT', 'HIGH', 'MEDIUM', 'LOW')),
    status              VARCHAR(12)   NOT NULL CHECK (status IN ('NEW', 'TRIAGED', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CLOSED')),
    assignee_id         UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    created_by          UUID          NOT NULL REFERENCES app_user (id),
    response_due_at     TIMESTAMPTZ   NOT NULL,
    response_warn_at    TIMESTAMPTZ   NOT NULL,
    resolution_due_at   TIMESTAMPTZ   NOT NULL,
    resolution_warn_at  TIMESTAMPTZ   NOT NULL,
    responded_at        TIMESTAMPTZ,
    resolved_at         TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, number)
);
CREATE INDEX ix_svc_ticket_status ON svc_ticket (org_id, status, created_at DESC);
CREATE INDEX ix_svc_ticket_assignee ON svc_ticket (org_id, assignee_id);

CREATE TABLE svc_ticket_comment (
    id          UUID           PRIMARY KEY,
    org_id      UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    ticket_id   UUID           NOT NULL REFERENCES svc_ticket (id) ON DELETE CASCADE,
    author_id   UUID           NOT NULL REFERENCES app_user (id),
    body        VARCHAR(4000)  NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_svc_ticket_comment ON svc_ticket_comment (ticket_id, created_at);

CREATE TABLE svc_ticket_attachment (
    id          UUID         PRIMARY KEY,
    org_id      UUID         NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    ticket_id   UUID         NOT NULL REFERENCES svc_ticket (id) ON DELETE CASCADE,
    file_id     UUID         NOT NULL REFERENCES stored_file (id) ON DELETE CASCADE,
    added_by    UUID         NOT NULL REFERENCES app_user (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_svc_ticket_attachment ON svc_ticket_attachment (ticket_id);

-- Preventive maintenance of one asset every interval_count DAYS or MONTHS. The next work order is generated
-- lead_days before next_due (by the hourly job or "Generate now"), then next_due moves one interval on.
CREATE TABLE svc_maintenance_plan (
    id                 UUID          PRIMARY KEY,
    org_id             UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    asset_id           UUID          NOT NULL REFERENCES svc_asset (id) ON DELETE CASCADE,
    title              VARCHAR(200)  NOT NULL,
    interval_count     SMALLINT      NOT NULL CHECK (interval_count BETWEEN 1 AND 366),
    interval_unit      VARCHAR(6)    NOT NULL CHECK (interval_unit IN ('DAYS', 'MONTHS')),
    next_due           DATE          NOT NULL,
    lead_days          SMALLINT      NOT NULL DEFAULT 7 CHECK (lead_days BETWEEN 0 AND 90),
    technician_id      UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    checklist          JSONB         NOT NULL DEFAULT '[]'::jsonb,
    active             BOOLEAN       NOT NULL DEFAULT TRUE,
    last_generated_at  TIMESTAMPTZ,
    created_by         UUID          NOT NULL REFERENCES app_user (id),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_svc_plan_due ON svc_maintenance_plan (org_id, next_due) WHERE active;

-- DRAFT ─schedule (approval when the total is above the threshold)─► SCHEDULED ─start─► IN_PROGRESS ⇄ ON_HOLD
-- ─complete─► COMPLETED; anything but COMPLETED ─► CANCELLED. Totals are Σ lines (half-up to cents).
-- created_by is NULL for work orders the maintenance job generated. (plan_id, plan_due) is unique, so a plan's
-- due date never gets two work orders.
CREATE TABLE svc_work_order (
    id                UUID           PRIMARY KEY,
    org_id            UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    number            VARCHAR(20)    NOT NULL,
    title             VARCHAR(200)   NOT NULL,
    description       VARCHAR(4000),
    priority          VARCHAR(8)     NOT NULL CHECK (priority IN ('URGENT', 'HIGH', 'MEDIUM', 'LOW')),
    status            VARCHAR(12)    NOT NULL CHECK (status IN ('DRAFT', 'SCHEDULED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED')),
    ticket_id         UUID           REFERENCES svc_ticket (id) ON DELETE SET NULL,
    customer_id       UUID           REFERENCES svc_customer (id),
    site_id           UUID           REFERENCES svc_site (id) ON DELETE SET NULL,
    asset_id          UUID           REFERENCES svc_asset (id) ON DELETE SET NULL,
    plan_id           UUID           REFERENCES svc_maintenance_plan (id) ON DELETE SET NULL,
    plan_due          DATE,
    scheduled_start   TIMESTAMPTZ,
    scheduled_end     TIMESTAMPTZ,
    parts_total       NUMERIC(12,2)  NOT NULL DEFAULT 0,
    labour_total      NUMERIC(12,2)  NOT NULL DEFAULT 0,
    total             NUMERIC(12,2)  NOT NULL DEFAULT 0,
    approval_state    VARCHAR(10)    NOT NULL DEFAULT 'NONE' CHECK (approval_state IN ('NONE', 'PENDING', 'APPROVED', 'REJECTED')),
    approval_id       UUID           REFERENCES approval_request (id) ON DELETE SET NULL,
    approved_total    NUMERIC(12,2),
    hold_reason       VARCHAR(500),
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    completed_by      UUID           REFERENCES app_user (id),
    completion_notes  VARCHAR(4000),
    signed_by         VARCHAR(120),
    cancel_reason     VARCHAR(500),
    created_by        UUID           REFERENCES app_user (id),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (org_id, number),
    CHECK (scheduled_end IS NULL OR scheduled_start IS NULL OR scheduled_end > scheduled_start),
    CHECK (status = 'DRAFT' OR status = 'CANCELLED' OR scheduled_start IS NOT NULL),
    CHECK (status <> 'COMPLETED' OR (completed_at IS NOT NULL AND signed_by IS NOT NULL))
);
CREATE INDEX ix_svc_wo_status ON svc_work_order (org_id, status, scheduled_start);
CREATE INDEX ix_svc_wo_ticket ON svc_work_order (ticket_id) WHERE ticket_id IS NOT NULL;
CREATE INDEX ix_svc_wo_asset ON svc_work_order (asset_id) WHERE asset_id IS NOT NULL;
CREATE UNIQUE INDEX ux_svc_wo_plan_due ON svc_work_order (plan_id, plan_due) WHERE plan_id IS NOT NULL;

CREATE TABLE svc_wo_technician (
    work_order_id  UUID  NOT NULL REFERENCES svc_work_order (id) ON DELETE CASCADE,
    user_id        UUID  NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    org_id         UUID  NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    PRIMARY KEY (work_order_id, user_id)
);
CREATE INDEX ix_svc_wo_technician_user ON svc_wo_technician (org_id, user_id);

CREATE TABLE svc_wo_checklist (
    id             UUID          PRIMARY KEY,
    org_id         UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    work_order_id  UUID          NOT NULL REFERENCES svc_work_order (id) ON DELETE CASCADE,
    position       SMALLINT      NOT NULL,
    label          VARCHAR(200)  NOT NULL,
    done           BOOLEAN       NOT NULL DEFAULT FALSE,
    done_by        UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    done_at        TIMESTAMPTZ
);
CREATE INDEX ix_svc_wo_checklist ON svc_wo_checklist (work_order_id, position);

-- A part or labour line: amount = quantity × unit price, rounded half-up to cents.
CREATE TABLE svc_wo_line (
    id             UUID           PRIMARY KEY,
    org_id         UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    work_order_id  UUID           NOT NULL REFERENCES svc_work_order (id) ON DELETE CASCADE,
    position       SMALLINT       NOT NULL,
    kind           VARCHAR(6)     NOT NULL CHECK (kind IN ('PART', 'LABOUR')),
    description    VARCHAR(300)   NOT NULL,
    quantity       NUMERIC(12,3)  NOT NULL CHECK (quantity > 0),
    unit_price     NUMERIC(12,2)  NOT NULL CHECK (unit_price >= 0),
    amount         NUMERIC(12,2)  NOT NULL
);
CREATE INDEX ix_svc_wo_line ON svc_wo_line (work_order_id, position);
