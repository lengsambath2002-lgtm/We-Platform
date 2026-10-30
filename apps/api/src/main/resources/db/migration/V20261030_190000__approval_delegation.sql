-- Acting approvers: while someone is away (from starts_on to ends_on, Asia/Phnom_Penh dates) their delegate decides
-- their approval steps on their behalf. Reminders and escalation: a workflow may remind the approver after some hours
-- and let the organisation's Admins decide after more.

CREATE TABLE approval_delegation (
    id           UUID          PRIMARY KEY,
    org_id       UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    user_id      UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    delegate_id  UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    starts_on    DATE          NOT NULL,
    ends_on      DATE          NOT NULL,
    reason       VARCHAR(300),
    created_by   UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    revoked_at   TIMESTAMPTZ,
    CHECK (delegate_id <> user_id),
    CHECK (ends_on >= starts_on)
);
CREATE INDEX approval_delegation_user ON approval_delegation (org_id, user_id);
CREATE INDEX approval_delegation_delegate ON approval_delegation (org_id, delegate_id);

ALTER TABLE approval_step ADD COLUMN activated_at TIMESTAMPTZ;
ALTER TABLE approval_step ADD COLUMN reminded_at TIMESTAMPTZ;
ALTER TABLE approval_step ADD COLUMN escalated_at TIMESTAMPTZ;
ALTER TABLE approval_step ADD COLUMN on_behalf_of UUID REFERENCES app_user (id);
UPDATE approval_step s SET activated_at = COALESCE(
    (SELECT max(p.decided_at) FROM approval_step p WHERE p.request_id = s.request_id AND p.step_order < s.step_order),
    (SELECT r.created_at FROM approval_request r WHERE r.id = s.request_id))
WHERE s.status = 'PENDING';

ALTER TABLE approval_action ADD COLUMN on_behalf_of UUID REFERENCES app_user (id);

ALTER TABLE workflow_definition ADD COLUMN remind_after_hours INT CHECK (remind_after_hours BETWEEN 1 AND 720);
ALTER TABLE workflow_definition ADD COLUMN escalate_after_hours INT CHECK (escalate_after_hours BETWEEN 1 AND 2160);
