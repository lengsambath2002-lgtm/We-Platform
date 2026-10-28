package com.businessplatform.report.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * The report aggregations, done by PostgreSQL ({@code GROUP BY}, {@code percentile_cont}) so no rows are loaded
 * into memory. Callers pass the projects the person may see; an empty collection means nothing to count.
 * Tasks are top-level only ({@code parent_id IS NULL}).
 */
@Repository
@RequiredArgsConstructor
public class ReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public record WeekCount(LocalDate weekStart, long count) {
    }

    public record StatusCount(UUID projectId, String status, long count, long overdue) {
    }

    public record AssigneeCount(UUID userId, String name, long open, long overdue) {
    }

    public record ApprovalRow(UUID workflowId, long submitted, long approved, long rejected, long changesRequested,
            long pending, long withdrawn, Double medianHours, Double p90Hours) {
    }

    /** Which approval requests count: all of the organisation's, or those about tasks in {@code projectIds}. */
    public record ApprovalScope(boolean orgWide, Collection<UUID> projectIds) {

        boolean none() {
            return !orgWide && projectIds.isEmpty();
        }
    }

    /** Top-level tasks created in [start, end), per week (Monday) in {@code zone}. */
    public List<WeekCount> createdPerWeek(Collection<UUID> projectIds, Instant start, Instant end, String zone) {
        return perWeek("created_at", projectIds, start, end, zone);
    }

    /** Top-level tasks completed (and still done) in [start, end), per week (Monday) in {@code zone}. */
    public List<WeekCount> completedPerWeek(Collection<UUID> projectIds, Instant start, Instant end, String zone) {
        return perWeek("completed_at", projectIds, start, end, zone);
    }

    private List<WeekCount> perWeek(String column, Collection<UUID> projectIds, Instant start, Instant end,
            String zone) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT CAST(date_trunc('week', " + column + " AT TIME ZONE :zone) AS date) AS wk, COUNT(*) AS n"
                + " FROM task WHERE project_id IN (:ids) AND parent_id IS NULL"
                + " AND " + column + " >= :start AND " + column + " < :end GROUP BY wk ORDER BY wk";
        var p = new MapSqlParameterSource("ids", projectIds).addValue("zone", zone)
                .addValue("start", Timestamp.from(start)).addValue("end", Timestamp.from(end));
        return jdbc.query(sql, p, (rs, i) -> new WeekCount(rs.getDate("wk").toLocalDate(), rs.getLong("n")));
    }

    /** Open top-level tasks per project and status, with how many are due before {@code today}. */
    public List<StatusCount> openByProjectAndStatus(Collection<UUID> projectIds, LocalDate today) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT project_id, status, COUNT(*) AS n, COUNT(*) FILTER (WHERE due_date < :today) AS late"
                + " FROM task WHERE project_id IN (:ids) AND parent_id IS NULL AND status <> 'DONE'"
                + " GROUP BY project_id, status";
        var p = new MapSqlParameterSource("ids", projectIds).addValue("today", java.sql.Date.valueOf(today));
        return jdbc.query(sql, p, (rs, i) -> new StatusCount(rs.getObject("project_id", UUID.class),
                rs.getString("status"), rs.getLong("n"), rs.getLong("late")));
    }

    /** Open top-level tasks per assignee (null = unassigned), most loaded first. */
    public List<AssigneeCount> openByAssignee(Collection<UUID> projectIds, LocalDate today) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT t.assignee_id, u.name, COUNT(*) AS n, COUNT(*) FILTER (WHERE t.due_date < :today) AS late"
                + " FROM task t LEFT JOIN app_user u ON u.id = t.assignee_id"
                + " WHERE t.project_id IN (:ids) AND t.parent_id IS NULL AND t.status <> 'DONE'"
                + " GROUP BY t.assignee_id, u.name ORDER BY n DESC, u.name NULLS LAST";
        var p = new MapSqlParameterSource("ids", projectIds).addValue("today", java.sql.Date.valueOf(today));
        return jdbc.query(sql, p, (rs, i) -> new AssigneeCount(rs.getObject("assignee_id", UUID.class),
                rs.getString("name"), rs.getLong("n"), rs.getLong("late")));
    }

    /**
     * Requests submitted in [start, end) by current status, and percentiles of hours to a final decision, per
     * workflow plus one overall row ({@code workflowId} null) via {@code GROUPING SETS}.
     */
    public List<ApprovalRow> approvals(UUID orgId, ApprovalScope scope, Instant start, Instant end) {
        if (scope.none()) {
            return List.of();
        }
        String hours = "EXTRACT(EPOCH FROM (decided_at - created_at)) / 3600.0";
        String decided = " FILTER (WHERE status IN ('APPROVED', 'REJECTED') AND decided_at IS NOT NULL)";
        String sql = "SELECT workflow_id, GROUPING(workflow_id) AS overall, COUNT(*) AS submitted,"
                + " COUNT(*) FILTER (WHERE status = 'APPROVED') AS approved,"
                + " COUNT(*) FILTER (WHERE status = 'REJECTED') AS rejected,"
                + " COUNT(*) FILTER (WHERE status = 'CHANGES_REQUESTED') AS changes,"
                + " COUNT(*) FILTER (WHERE status = 'PENDING') AS pending,"
                + " COUNT(*) FILTER (WHERE status = 'WITHDRAWN') AS withdrawn,"
                + " percentile_cont(0.5) WITHIN GROUP (ORDER BY " + hours + ")" + decided + " AS median,"
                + " percentile_cont(0.9) WITHIN GROUP (ORDER BY " + hours + ")" + decided + " AS p90"
                + " FROM approval_request r WHERE org_id = :org AND created_at >= :start AND created_at < :end"
                + scopeSql(scope) + " GROUP BY GROUPING SETS ((workflow_id), ())";
        var p = params(orgId, scope).addValue("start", Timestamp.from(start)).addValue("end", Timestamp.from(end));
        return jdbc.query(sql, p, (rs, i) -> new ApprovalRow(
                rs.getInt("overall") == 1 ? null : rs.getObject("workflow_id", UUID.class), rs.getLong("submitted"),
                rs.getLong("approved"), rs.getLong("rejected"), rs.getLong("changes"), rs.getLong("pending"),
                rs.getLong("withdrawn"), (Double) rs.getObject("median"), (Double) rs.getObject("p90")));
    }

    /** Requests still pending that were submitted before {@code cutoff}, per workflow. */
    public Map<UUID, Long> stalePending(UUID orgId, ApprovalScope scope, Instant cutoff) {
        Map<UUID, Long> out = new HashMap<>();
        if (scope.none()) {
            return out;
        }
        String sql = "SELECT workflow_id, COUNT(*) AS n FROM approval_request r WHERE org_id = :org"
                + " AND status = 'PENDING' AND created_at < :cutoff" + scopeSql(scope) + " GROUP BY workflow_id";
        jdbc.query(sql, params(orgId, scope).addValue("cutoff", Timestamp.from(cutoff)),
                rs -> {
                    out.put(rs.getObject("workflow_id", UUID.class), rs.getLong("n"));
                });
        return out;
    }

    /** The organisation's workflow names by id. */
    public Map<UUID, String> workflowNames(UUID orgId) {
        Map<UUID, String> out = new HashMap<>();
        jdbc.query("SELECT id, name FROM workflow_definition WHERE org_id = :org",
                new MapSqlParameterSource("org", orgId), rs -> {
                    out.put(rs.getObject("id", UUID.class), rs.getString("name"));
                });
        return out;
    }

    private static String scopeSql(ApprovalScope scope) {
        return scope.orgWide() ? ""
                : " AND module_key = 'projects' AND record_type = 'task'"
                        + " AND record_id IN (SELECT id FROM task WHERE project_id IN (:ids))";
    }

    private static MapSqlParameterSource params(UUID orgId, ApprovalScope scope) {
        var p = new MapSqlParameterSource("org", orgId);
        if (!scope.orgWide()) {
            p.addValue("ids", scope.projectIds());
        }
        return p;
    }
}
