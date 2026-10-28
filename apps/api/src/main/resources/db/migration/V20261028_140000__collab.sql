-- Collaboration: per-person choice of in-app notification kinds, and the audit log's person filter.

-- A row = this member does not want in-app notifications of this kind in this organisation.
CREATE TABLE notification_mute (
    membership_id  UUID         NOT NULL REFERENCES membership (id) ON DELETE CASCADE,
    kind           VARCHAR(40)  NOT NULL,
    PRIMARY KEY (membership_id, kind)
);

CREATE INDEX ix_activity_org_actor ON activity (org_id, actor_id, created_at DESC);
CREATE INDEX ix_notification_user_org ON notification (user_id, org_id, created_at DESC);
