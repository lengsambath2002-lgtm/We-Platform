-- Notifications by email too (as VirtualBiz): every notification is also emailed, after the change is saved, unless
-- the person switched email off for that kind in that organisation. A row = no email of this kind.
CREATE TABLE notification_email_off (
    membership_id  UUID         NOT NULL REFERENCES membership (id) ON DELETE CASCADE,
    kind           VARCHAR(40)  NOT NULL,
    PRIMARY KEY (membership_id, kind)
);
