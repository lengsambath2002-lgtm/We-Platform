package com.businessplatform.report.payload;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Report aggregates. Task numbers count top-level tasks (a subtask is part of its parent), like project progress. */
public final class ReportPayloads {

    private ReportPayloads() {
    }

    /** Tasks created and completed in each week (Monday start) that overlaps {@code from}–{@code to}. */
    public record TaskFlow(LocalDate from, LocalDate to, long created, long completed, List<WeekFlow> weeks) {
    }

    public record WeekFlow(LocalDate weekStart, long created, long completed) {
    }

    /** Open (not done) tasks right now, per project and status; overdue = due before today. */
    public record OpenWork(LocalDate asOf, long open, long overdue, List<ProjectOpen> projects) {
    }

    public record ProjectOpen(String key, String name, long todo, long inProgress, long inReview, long open,
            long overdue) {
    }

    /** Open tasks right now per assignee; {@code id} and {@code name} are null for unassigned work. */
    public record Workload(LocalDate asOf, long open, long overdue, List<AssigneeLoad> people) {
    }

    public record AssigneeLoad(UUID id, String name, long open, long overdue) {
    }

    /**
     * Approval requests submitted in {@code from}–{@code to}, by current outcome, with hours from submission to
     * the final decision (approved or rejected). {@code stale} counts requests pending for more than
     * {@code staleDays} days right now, whenever they were submitted.
     */
    public record Approvals(LocalDate from, LocalDate to, int staleDays, ApprovalStats total,
            List<WorkflowStats> workflows) {
    }

    public record WorkflowStats(UUID id, String name, ApprovalStats stats) {
    }

    public record ApprovalStats(long submitted, long approved, long rejected, long changesRequested, long pending,
            long withdrawn, Double medianHours, Double p90Hours, long stale) {
    }
}
