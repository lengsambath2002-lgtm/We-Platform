-- Public holidays per organisation: leave doesn't count them, and the calendar shows them.
CREATE TABLE hr_holiday (
    id            UUID          PRIMARY KEY,
    org_id        UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    holiday_date  DATE          NOT NULL,
    name_en       VARCHAR(120)  NOT NULL,
    name_km       VARCHAR(120)  NOT NULL,
    created_by    UUID          REFERENCES app_user (id),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (org_id, holiday_date)
);
