-- Organisation structure: units (branch, division, department, center, section, team) in a tree under the
-- organisation, positions (job titles with a planned headcount) and who holds which (one primary assignment
-- per member, any number of additional ones). Replaces the unused branch table and the free-text department.

CREATE TABLE org_unit (
    id            UUID          PRIMARY KEY,
    org_id        UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    parent_id     UUID          REFERENCES org_unit (id),
    kind          VARCHAR(12)   NOT NULL CHECK (kind IN ('BRANCH', 'DIVISION', 'DEPARTMENT', 'CENTER', 'SECTION', 'TEAM')),
    name          VARCHAR(120)  NOT NULL,
    code          VARCHAR(20),
    head_user_id  UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    address       VARCHAR(300),
    phone         VARCHAR(30),
    archived_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX org_unit_parent ON org_unit (org_id, parent_id);
CREATE UNIQUE INDEX org_unit_code ON org_unit (org_id, lower(code)) WHERE code IS NOT NULL;
-- Names are unique among a unit's active children (and among the active top-level units).
CREATE UNIQUE INDEX org_unit_name ON org_unit (org_id, COALESCE(parent_id, org_id), lower(name)) WHERE archived_at IS NULL;

CREATE TABLE org_position (
    id             UUID           PRIMARY KEY,
    org_id         UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    unit_id        UUID           REFERENCES org_unit (id),
    title          VARCHAR(80)    NOT NULL,
    code           VARCHAR(20),
    grade          VARCHAR(20),
    reports_to_id  UUID           REFERENCES org_position (id) ON DELETE SET NULL,
    headcount      INT            NOT NULL DEFAULT 1 CHECK (headcount BETWEEN 0 AND 10000),
    description    VARCHAR(2000),
    archived_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (reports_to_id IS NULL OR reports_to_id <> id)
);
CREATE INDEX org_position_unit ON org_position (org_id, unit_id);
CREATE UNIQUE INDEX org_position_code ON org_position (org_id, lower(code)) WHERE code IS NOT NULL;
CREATE UNIQUE INDEX org_position_title ON org_position (org_id, COALESCE(unit_id, org_id), lower(title)) WHERE archived_at IS NULL;

CREATE TABLE org_assignment (
    id             UUID          PRIMARY KEY,
    org_id         UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    membership_id  UUID          NOT NULL REFERENCES membership (id) ON DELETE CASCADE,
    unit_id        UUID          REFERENCES org_unit (id),
    position_id    UUID          REFERENCES org_position (id),
    is_primary     BOOLEAN       NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (unit_id IS NOT NULL OR position_id IS NOT NULL)
);
CREATE INDEX org_assignment_org ON org_assignment (org_id);
CREATE UNIQUE INDEX org_assignment_primary ON org_assignment (membership_id) WHERE is_primary;
CREATE UNIQUE INDEX org_assignment_once ON org_assignment (membership_id, COALESCE(unit_id, membership_id), COALESCE(position_id, membership_id));
CREATE INDEX org_assignment_unit ON org_assignment (unit_id);
CREATE INDEX org_assignment_position ON org_assignment (position_id);

-- ── What there was before ────────────────────────────────────────────────────────────

-- Branches become top-level BRANCH units (same ids); a member's branch becomes their primary assignment.
INSERT INTO org_unit (id, org_id, kind, name, address, created_at, updated_at)
SELECT id, org_id, 'BRANCH', name, address, created_at, created_at FROM branch;

-- Each distinct free-text department becomes a top-level DEPARTMENT unit.
INSERT INTO org_unit (id, org_id, kind, name)
SELECT gen_random_uuid(), d.org_id, 'DEPARTMENT', d.name
FROM (SELECT DISTINCT ON (p.org_id, lower(btrim(p.department))) p.org_id, btrim(p.department) AS name
      FROM employee_profile p WHERE btrim(COALESCE(p.department, '')) <> ''
      ORDER BY p.org_id, lower(btrim(p.department)), btrim(p.department)) d
WHERE NOT EXISTS (SELECT 1 FROM org_unit u WHERE u.org_id = d.org_id AND u.parent_id IS NULL AND lower(u.name) = lower(d.name));

-- Each member's job title becomes a position in their department (or at the top), sized to its holders.
WITH held AS (
    SELECT m.org_id, m.id AS membership_id, btrim(m.title) AS title,
           (SELECT u.id FROM org_unit u WHERE u.org_id = m.org_id AND u.parent_id IS NULL
               AND ((btrim(COALESCE(p.department, '')) <> '' AND u.kind = 'DEPARTMENT' AND lower(u.name) = lower(btrim(p.department)))
                 OR (btrim(COALESCE(p.department, '')) = '' AND u.id = m.branch_id))
            ORDER BY u.kind LIMIT 1) AS unit_id
    FROM membership m LEFT JOIN employee_profile p ON p.membership_id = m.id
    WHERE m.role <> 'GUEST'
)
INSERT INTO org_position (id, org_id, unit_id, title, headcount)
SELECT gen_random_uuid(), org_id, unit_id, min(title), count(*)
FROM held WHERE btrim(COALESCE(title, '')) <> ''
GROUP BY org_id, unit_id, lower(title);

WITH held AS (
    SELECT m.org_id, m.id AS membership_id, m.created_at, btrim(COALESCE(m.title, '')) AS title,
           (SELECT u.id FROM org_unit u WHERE u.org_id = m.org_id AND u.parent_id IS NULL
               AND ((btrim(COALESCE(p.department, '')) <> '' AND u.kind = 'DEPARTMENT' AND lower(u.name) = lower(btrim(p.department)))
                 OR (btrim(COALESCE(p.department, '')) = '' AND u.id = m.branch_id))
            ORDER BY u.kind LIMIT 1) AS unit_id
    FROM membership m LEFT JOIN employee_profile p ON p.membership_id = m.id
    WHERE m.role <> 'GUEST'
)
INSERT INTO org_assignment (id, org_id, membership_id, unit_id, position_id, is_primary, created_at)
SELECT gen_random_uuid(), h.org_id, h.membership_id, h.unit_id,
       (SELECT x.id FROM org_position x WHERE x.org_id = h.org_id AND x.unit_id IS NOT DISTINCT FROM h.unit_id
           AND h.title <> '' AND lower(x.title) = lower(h.title) LIMIT 1),
       TRUE, h.created_at
FROM held h
WHERE h.unit_id IS NOT NULL OR h.title <> '';

ALTER TABLE membership DROP COLUMN branch_id;
DROP TABLE branch;

-- A member's department, as HR shows it: the nearest DEPARTMENT at or above their primary unit (or their primary
-- position's unit), else that unit's own name. HR keeps employee_profile.department equal to it.
CREATE FUNCTION org_department_of(member UUID) RETURNS VARCHAR(120) LANGUAGE sql STABLE AS $$
    WITH RECURSIVE up AS (
        SELECT u.id, u.parent_id, u.kind, u.name, 0 AS depth
        FROM org_assignment a
        JOIN org_unit u ON u.id = COALESCE(a.unit_id, (SELECT x.unit_id FROM org_position x WHERE x.id = a.position_id))
        WHERE a.membership_id = member AND a.is_primary
      UNION ALL
        SELECT u.id, u.parent_id, u.kind, u.name, up.depth + 1
        FROM up JOIN org_unit u ON u.id = up.parent_id
        WHERE up.kind <> 'DEPARTMENT' AND up.depth < 30
    )
    SELECT name FROM up ORDER BY (kind = 'DEPARTMENT') DESC, depth LIMIT 1
$$;
