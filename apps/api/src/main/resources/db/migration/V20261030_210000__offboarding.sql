-- Offboarding: a resignation (REQUESTED by the person) or a departure HR starts (IN_PROGRESS), with a checklist built
-- from the organisation's template, COMPLETED on or after the last day (access is then removed) or CANCELLED.

CREATE TABLE offboarding (
    id            UUID           PRIMARY KEY,
    org_id        UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    user_id       UUID           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind          VARCHAR(16)    NOT NULL CHECK (kind IN ('RESIGNATION', 'TERMINATION', 'END_OF_CONTRACT', 'RETIREMENT', 'OTHER')),
    status        VARCHAR(12)    NOT NULL CHECK (status IN ('REQUESTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    notice_on     DATE           NOT NULL,
    last_day      DATE           NOT NULL,
    reason        VARCHAR(1000),
    rehire        BOOLEAN,
    requested_by  UUID           REFERENCES app_user (id) ON DELETE SET NULL,
    started_by    UUID           REFERENCES app_user (id) ON DELETE SET NULL,
    completed_by  UUID           REFERENCES app_user (id) ON DELETE SET NULL,
    completed_at  TIMESTAMPTZ,
    cancelled_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (last_day >= notice_on)
);
CREATE UNIQUE INDEX offboarding_open ON offboarding (org_id, user_id) WHERE status IN ('REQUESTED', 'IN_PROGRESS');
CREATE INDEX offboarding_org ON offboarding (org_id, status);

CREATE TABLE offboarding_item (
    id               UUID          PRIMARY KEY,
    org_id           UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    offboarding_id   UUID          NOT NULL REFERENCES offboarding (id) ON DELETE CASCADE,
    title            VARCHAR(200)  NOT NULL,
    due_date         DATE,
    done_at          TIMESTAMPTZ,
    done_by          UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    sort_order       SMALLINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX offboarding_item_parent ON offboarding_item (offboarding_id, sort_order);

-- due_days: relative to the last day (negative = before it).
CREATE TABLE offboarding_template (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    title       VARCHAR(200)  NOT NULL,
    due_days    SMALLINT      NOT NULL DEFAULT 0 CHECK (due_days BETWEEN -90 AND 90),
    sort_order  SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX offboarding_template_org ON offboarding_template (org_id, sort_order);

ALTER TABLE employee_profile ADD COLUMN left_on DATE;
