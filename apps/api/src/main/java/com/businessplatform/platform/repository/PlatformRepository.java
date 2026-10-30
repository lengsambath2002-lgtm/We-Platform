package com.businessplatform.platform.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.businessplatform.platform.payload.PlatformPayloads.AuditRow;
import com.businessplatform.platform.payload.PlatformPayloads.MemberRow;
import com.businessplatform.platform.payload.PlatformPayloads.OrgRow;
import com.businessplatform.platform.payload.PlatformPayloads.Person;
import com.businessplatform.platform.payload.PlatformPayloads.UserOrg;
import com.businessplatform.platform.payload.PlatformPayloads.UserRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * The console's lists and counts across every organisation and account, done by PostgreSQL. Searches match
 * names, web addresses and emails case-insensitively; lists are newest first.
 */
@Repository
@RequiredArgsConstructor
public class PlatformRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public record Page<T>(List<T> items, long total) {
    }

    // ── Counts ───────────────────────────────────────────────────────────────────────

    public record Counts(long organisations, long suspendedOrganisations, long deletedOrganisations,
            long newOrganisations, long users, long disabledUsers, long newUsers, long activeUsers, long superAdmins) {
    }

    /** {@code new*} and {@code activeUsers}: since {@code since}; {@code organisations}: not deleted. */
    public Counts counts(Instant since) {
        return jdbc.queryForObject("""
                SELECT
                  (SELECT count(*) FROM organisation WHERE deleted_at IS NULL) AS orgs,
                  (SELECT count(*) FROM organisation WHERE deleted_at IS NULL AND suspended_at IS NOT NULL) AS suspended,
                  (SELECT count(*) FROM organisation WHERE deleted_at IS NOT NULL) AS deleted,
                  (SELECT count(*) FROM organisation WHERE deleted_at IS NULL AND created_at >= :since) AS new_orgs,
                  (SELECT count(*) FROM app_user) AS users,
                  (SELECT count(*) FROM app_user WHERE disabled_at IS NOT NULL) AS disabled,
                  (SELECT count(*) FROM app_user WHERE created_at >= :since) AS new_users,
                  (SELECT count(DISTINCT user_id) FROM user_session WHERE last_used_at >= :since) AS active_users,
                  (SELECT count(*) FROM app_user WHERE super_admin) AS super_admins""",
                new MapSqlParameterSource("since", Timestamp.from(since)),
                (rs, i) -> new Counts(rs.getLong("orgs"), rs.getLong("suspended"), rs.getLong("deleted"),
                        rs.getLong("new_orgs"), rs.getLong("users"), rs.getLong("disabled"), rs.getLong("new_users"),
                        rs.getLong("active_users"), rs.getLong("super_admins")));
    }

    /** Live organisations (neither deleted nor suspended) with each module switched on. */
    public Map<String, Long> moduleAdoption() {
        Map<String, Long> out = new HashMap<>();
        jdbc.query("""
                SELECT om.module_key, count(*) AS n FROM org_module om JOIN organisation o ON o.id = om.org_id
                WHERE om.enabled AND o.deleted_at IS NULL AND o.suspended_at IS NULL GROUP BY om.module_key""",
                rs -> {
                    out.put(rs.getString("module_key"), rs.getLong("n"));
                });
        return out;
    }

    // ── Organisations ────────────────────────────────────────────────────────────────

    private static final String ORG_COLUMNS = """
            o.id, o.slug, o.name, o.created_at, o.deleted_at, o.suspended_at,
            (SELECT count(*) FROM membership m WHERE m.org_id = o.id AND m.status = 'ACTIVE') AS members,
            (SELECT max(a.created_at) FROM activity a WHERE a.org_id = o.id) AS last_activity,
            (SELECT string_agg(om.module_key, ',' ORDER BY om.module_key) FROM org_module om
              WHERE om.org_id = o.id AND om.enabled) AS modules""";

    /** {@code status}: ACTIVE, SUSPENDED, DELETED, or null for all. */
    public Page<OrgRow> organisations(String q, String status, int page, int size) {
        MapSqlParameterSource p = search(q).addValue("size", size).addValue("offset", page * size);
        String where = " WHERE (:q = '' OR o.name ILIKE :like ESCAPE '\\' OR o.slug ILIKE :like ESCAPE '\\')"
                + switch (status == null ? "" : status) {
                    case "ACTIVE" -> " AND o.deleted_at IS NULL AND o.suspended_at IS NULL";
                    case "SUSPENDED" -> " AND o.deleted_at IS NULL AND o.suspended_at IS NOT NULL";
                    case "DELETED" -> " AND o.deleted_at IS NOT NULL";
                    default -> "";
                };
        Long total = jdbc.queryForObject("SELECT count(*) FROM organisation o" + where, p, Long.class);
        List<OrgRow> rows = withOwners(jdbc.query("SELECT " + ORG_COLUMNS + " FROM organisation o" + where
                + " ORDER BY o.created_at DESC, o.id LIMIT :size OFFSET :offset", p, (rs, i) -> org(rs, List.of())));
        return new Page<>(rows, total == null ? 0 : total);
    }

    public OrgRow organisation(UUID id) {
        List<OrgRow> rows = jdbc.query("SELECT " + ORG_COLUMNS + " FROM organisation o WHERE o.id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> org(rs, List.of()));
        return rows.isEmpty() ? null : withOwners(rows).get(0);
    }

    public List<MemberRow> members(UUID orgId) {
        return jdbc.query("""
                SELECT u.id, u.name, u.email, m.role, m.status, u.disabled_at, m.created_at
                FROM membership m JOIN app_user u ON u.id = m.user_id WHERE m.org_id = :org
                ORDER BY CASE m.role WHEN 'OWNER' THEN 0 WHEN 'ADMIN' THEN 1 WHEN 'MEMBER' THEN 2 ELSE 3 END, u.name""",
                new MapSqlParameterSource("org", orgId),
                (rs, i) -> new MemberRow(uuid(rs, "id"), rs.getString("name"), rs.getString("email"), rs.getString("role"),
                        rs.getString("status"), rs.getTimestamp("disabled_at") != null, instant(rs, "created_at")));
    }

    private List<OrgRow> withOwners(List<OrgRow> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Map<UUID, List<Person>> owners = new HashMap<>();
        jdbc.query("""
                SELECT m.org_id, u.id, u.name, u.email FROM membership m JOIN app_user u ON u.id = m.user_id
                WHERE m.org_id IN (:ids) AND m.role = 'OWNER' AND m.status = 'ACTIVE' ORDER BY u.name""",
                new MapSqlParameterSource("ids", rows.stream().map(OrgRow::id).toList()), rs -> {
                    owners.computeIfAbsent(uuid(rs, "org_id"), k -> new ArrayList<>())
                            .add(new Person(uuid(rs, "id"), rs.getString("name"), rs.getString("email")));
                });
        return rows.stream().map(r -> new OrgRow(r.id(), r.slug(), r.name(), r.status(), r.members(),
                owners.getOrDefault(r.id(), List.of()), r.modules(), r.createdAt(), r.lastActivityAt())).toList();
    }

    private static OrgRow org(ResultSet rs, List<Person> owners) throws SQLException {
        String modules = rs.getString("modules");
        String status = rs.getTimestamp("deleted_at") != null ? "DELETED"
                : rs.getTimestamp("suspended_at") != null ? "SUSPENDED" : "ACTIVE";
        return new OrgRow(uuid(rs, "id"), rs.getString("slug"), rs.getString("name"), status, rs.getLong("members"),
                owners, modules == null ? List.of() : Arrays.asList(modules.split(",")), instant(rs, "created_at"),
                instant(rs, "last_activity"));
    }

    // ── Accounts ─────────────────────────────────────────────────────────────────────

    private static final String USER_COLUMNS = """
            u.id, u.name, u.email, u.avatar_url, u.super_admin, u.disabled_at, u.locked_until, u.created_at,
            (SELECT count(*) FROM membership m JOIN organisation o ON o.id = m.org_id
              WHERE m.user_id = u.id AND m.status = 'ACTIVE' AND o.deleted_at IS NULL) AS orgs,
            (SELECT max(s.last_used_at) FROM user_session s WHERE s.user_id = u.id) AS last_seen""";

    /** {@code status}: ACTIVE, DISABLED, SUPER_ADMIN, or null for all. */
    public Page<UserRow> users(String q, String status, Instant now, int page, int size) {
        MapSqlParameterSource p = search(q).addValue("size", size).addValue("offset", page * size);
        String where = " WHERE (:q = '' OR u.name ILIKE :like ESCAPE '\\' OR u.email ILIKE :like ESCAPE '\\')"
                + switch (status == null ? "" : status) {
                    case "ACTIVE" -> " AND u.disabled_at IS NULL";
                    case "DISABLED" -> " AND u.disabled_at IS NOT NULL";
                    case "SUPER_ADMIN" -> " AND u.super_admin";
                    default -> "";
                };
        Long total = jdbc.queryForObject("SELECT count(*) FROM app_user u" + where, p, Long.class);
        List<UserRow> rows = jdbc.query("SELECT " + USER_COLUMNS + " FROM app_user u" + where
                + " ORDER BY u.created_at DESC, u.id LIMIT :size OFFSET :offset", p, (rs, i) -> user(rs, now));
        return new Page<>(rows, total == null ? 0 : total);
    }

    public UserRow user(UUID id, Instant now) {
        List<UserRow> rows = jdbc.query("SELECT " + USER_COLUMNS + " FROM app_user u WHERE u.id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> user(rs, now));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<UserOrg> userOrgs(UUID userId) {
        return jdbc.query("""
                SELECT o.id, o.slug, o.name, m.role, o.deleted_at, o.suspended_at
                FROM membership m JOIN organisation o ON o.id = m.org_id
                WHERE m.user_id = :user AND m.status = 'ACTIVE' ORDER BY o.name""",
                new MapSqlParameterSource("user", userId),
                (rs, i) -> new UserOrg(uuid(rs, "id"), rs.getString("slug"), rs.getString("name"), rs.getString("role"),
                        rs.getTimestamp("deleted_at") != null ? "DELETED"
                                : rs.getTimestamp("suspended_at") != null ? "SUSPENDED" : "ACTIVE"));
    }

    private static UserRow user(ResultSet rs, Instant now) throws SQLException {
        Timestamp locked = rs.getTimestamp("locked_until");
        String status = rs.getTimestamp("disabled_at") != null ? "DISABLED"
                : locked != null && locked.toInstant().isAfter(now) ? "LOCKED" : "ACTIVE";
        return new UserRow(uuid(rs, "id"), rs.getString("name"), rs.getString("email"), rs.getString("avatar_url"),
                rs.getBoolean("super_admin"), status, rs.getLong("orgs"), instant(rs, "created_at"),
                instant(rs, "last_seen"));
    }

    // ── Audit ────────────────────────────────────────────────────────────────────────

    /** {@code targetId}: only entries about that organisation or account, or null for all. */
    public Page<AuditRow> audit(UUID targetId, int page, int size) {
        MapSqlParameterSource p = new MapSqlParameterSource("target", targetId).addValue("size", size)
                .addValue("offset", page * size);
        String where = targetId == null ? "" : " WHERE a.target_id = :target";
        Long total = jdbc.queryForObject("SELECT count(*) FROM platform_audit a" + where, p, Long.class);
        List<AuditRow> rows = jdbc.query("""
                SELECT a.id, a.action, a.target_type, a.target_id, a.data::text AS data, a.created_at,
                       actor.id AS actor_id, actor.name AS actor_name, actor.email AS actor_email,
                       COALESCE(o.name, u.name) AS target_name
                FROM platform_audit a
                LEFT JOIN app_user actor ON actor.id = a.actor_id
                LEFT JOIN organisation o ON a.target_type = 'organisation' AND o.id = a.target_id
                LEFT JOIN app_user u ON a.target_type = 'user' AND u.id = a.target_id""" + where
                + " ORDER BY a.created_at DESC, a.id DESC LIMIT :size OFFSET :offset", p,
                (rs, i) -> new AuditRow(uuid(rs, "id"),
                        rs.getString("actor_id") == null ? null
                                : new Person(uuid(rs, "actor_id"), rs.getString("actor_name"), rs.getString("actor_email")),
                        rs.getString("action"), rs.getString("target_type"), uuid(rs, "target_id"),
                        rs.getString("target_name"), data(rs.getString("data")), instant(rs, "created_at")));
        return new Page<>(rows, total == null ? 0 : total);
    }

    private Map<String, Object> data(String text) {
        try {
            return json.readValue(text, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private static MapSqlParameterSource search(String q) {
        String s = q == null ? "" : q.strip();
        String like = "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return new MapSqlParameterSource("q", s).addValue("like", like);
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
