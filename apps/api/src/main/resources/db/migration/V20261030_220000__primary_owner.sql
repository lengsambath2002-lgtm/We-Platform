-- One primary owner per organisation (as Slack's Primary Owner): only they delete it or hand primary ownership over;
-- they can't be demoted, suspended or leave until they do. Other Owners keep every other Owner right.
ALTER TABLE organisation ADD COLUMN primary_owner_id UUID REFERENCES app_user (id);
UPDATE organisation o SET primary_owner_id = COALESCE(
    (SELECT m.user_id FROM membership m WHERE m.org_id = o.id AND m.user_id = o.created_by AND m.role = 'OWNER' AND m.status = 'ACTIVE'),
    (SELECT m.user_id FROM membership m WHERE m.org_id = o.id AND m.role = 'OWNER' AND m.status = 'ACTIVE' ORDER BY m.created_at LIMIT 1));
