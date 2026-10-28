package com.businessplatform.report.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.project.domain.Project;
import com.businessplatform.project.service.ProjectAccess;
import com.businessplatform.project.service.TaskService;
import com.businessplatform.report.payload.ReportPayloads.*;
import com.businessplatform.report.repository.ReportRepository;
import com.businessplatform.report.repository.ReportRepository.ApprovalRow;
import com.businessplatform.report.repository.ReportRepository.ApprovalScope;

import lombok.RequiredArgsConstructor;

/**
 * Reports over what the caller may see (a peer of Overview: it reads Projects and Workflow, the core never reads
 * it). Owners, Admins and Members may open reports; Guests get FORBIDDEN, since a guest's view is a few shared
 * projects, not the organisation's work. Task numbers count only projects the caller may see
 * ({@link TaskService#visibleProjects}); approvals count requests about tasks in those projects, and for
 * Owners / Admins without a project filter, every request in the organisation. Dates are in
 * {@link CalendarService#ZONE}; a range is inclusive and at most {@value #MAX_DAYS} days.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    static final int MAX_DAYS = 366;

    private final OrgAccess orgAccess;
    private final ProjectAccess projectAccess;
    private final ModuleService modules;
    private final TaskService tasks;
    private final ReportRepository reports;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TaskFlow taskFlow(String slug, LocalDate from, LocalDate to, String project) {
        OrgContext ctx = taskContext(slug);
        Range r = range(from, to);
        Set<UUID> ids = projects(ctx, project).keySet();
        String zone = CalendarService.ZONE.getId();
        Map<LocalDate, Long> created = weeks(reports.createdPerWeek(ids, r.start(), r.end(), zone));
        Map<LocalDate, Long> completed = weeks(reports.completedPerWeek(ids, r.start(), r.end(), zone));
        List<WeekFlow> weeks = new ArrayList<>();
        for (LocalDate w = r.from().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); !w.isAfter(r.to());
                w = w.plusWeeks(1)) {
            weeks.add(new WeekFlow(w, created.getOrDefault(w, 0L), completed.getOrDefault(w, 0L)));
        }
        return new TaskFlow(r.from(), r.to(), sum(created), sum(completed), weeks);
    }

    @Transactional(readOnly = true)
    public OpenWork openWork(String slug, String project) {
        OrgContext ctx = taskContext(slug);
        Map<UUID, Project> visible = projects(ctx, project);
        LocalDate today = today();
        Map<UUID, long[]> counts = new LinkedHashMap<>(); // todo, in progress, in review, overdue
        for (var c : reports.openByProjectAndStatus(visible.keySet(), today)) {
            long[] n = counts.computeIfAbsent(c.projectId(), k -> new long[4]);
            switch (c.status()) {
                case "TODO" -> n[0] += c.count();
                case "IN_PROGRESS" -> n[1] += c.count();
                default -> n[2] += c.count();
            }
            n[3] += c.overdue();
        }
        List<ProjectOpen> rows = counts.entrySet().stream().map(e -> {
            Project p = visible.get(e.getKey());
            long[] n = e.getValue();
            return new ProjectOpen(p.getKey(), p.getName(), n[0], n[1], n[2], n[0] + n[1] + n[2], n[3]);
        }).sorted(Comparator.comparingLong(ProjectOpen::open).reversed().thenComparing(ProjectOpen::name)).toList();
        return new OpenWork(today, rows.stream().mapToLong(ProjectOpen::open).sum(),
                rows.stream().mapToLong(ProjectOpen::overdue).sum(), rows);
    }

    @Transactional(readOnly = true)
    public Workload workload(String slug, String project) {
        OrgContext ctx = taskContext(slug);
        LocalDate today = today();
        List<AssigneeLoad> people = reports.openByAssignee(projects(ctx, project).keySet(), today).stream()
                .map(a -> new AssigneeLoad(a.userId(), a.name(), a.open(), a.overdue())).toList();
        return new Workload(today, people.stream().mapToLong(AssigneeLoad::open).sum(),
                people.stream().mapToLong(AssigneeLoad::overdue).sum(), people);
    }

    @Transactional(readOnly = true)
    public Approvals approvals(String slug, LocalDate from, LocalDate to, String project, Integer staleDays) {
        OrgContext ctx = reportContext(slug);
        Range r = range(from, to);
        int days = staleDays == null ? 7 : staleDays;
        if (days < 1 || days > 365) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "staleDays", "Between 1 and 365 days");
        }
        boolean projectsOn = modules.enabled(ctx.orgId()).contains(Module.PROJECTS);
        ApprovalScope scope;
        if (project == null && ctx.isAdmin()) {
            scope = new ApprovalScope(true, Set.of());
        } else if (projectsOn) {
            scope = new ApprovalScope(false, projects(ctx, project).keySet());
        } else if (project != null) {
            throw BusinessException.notFound();
        } else {
            scope = new ApprovalScope(false, Set.of());
        }
        List<ApprovalRow> rows = reports.approvals(ctx.orgId(), scope, r.start(), r.end());
        Map<UUID, Long> stale = reports.stalePending(ctx.orgId(), scope,
                clock.instant().minus(days, ChronoUnit.DAYS));
        Map<UUID, String> names = reports.workflowNames(ctx.orgId());
        Map<UUID, ApprovalRow> byWorkflow = new HashMap<>();
        ApprovalRow overall = null;
        for (ApprovalRow row : rows) {
            if (row.workflowId() == null) {
                overall = row;
            } else {
                byWorkflow.put(row.workflowId(), row);
            }
        }
        List<WorkflowStats> workflows = new ArrayList<>();
        Set<UUID> ids = new HashSet<>(byWorkflow.keySet());
        ids.addAll(stale.keySet());
        for (UUID id : ids) {
            workflows.add(new WorkflowStats(id, names.getOrDefault(id, "?"),
                    stats(byWorkflow.get(id), stale.getOrDefault(id, 0L))));
        }
        workflows.sort(Comparator.comparingLong((WorkflowStats w) -> w.stats().submitted()).reversed()
                .thenComparing(WorkflowStats::name));
        return new Approvals(r.from(), r.to(), days, stats(overall, sum(stale)), workflows);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private record Range(LocalDate from, LocalDate to, Instant start, Instant end) {
    }

    /** Validates an inclusive date range; defaults to the last 30 days ending today. */
    private Range range(LocalDate from, LocalDate to) {
        LocalDate t = to == null ? today() : to;
        LocalDate f = from == null ? t.minusDays(29) : from;
        if (f.isAfter(t)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "from",
                    "The start date must be on or before the end date");
        }
        if (ChronoUnit.DAYS.between(f, t) + 1 > MAX_DAYS) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "to", "Choose at most 366 days");
        }
        return new Range(f, t, f.atStartOfDay(CalendarService.ZONE).toInstant(),
                t.plusDays(1).atStartOfDay(CalendarService.ZONE).toInstant());
    }

    /** Any report: an active non-guest member. */
    private OrgContext reportContext(String slug) {
        OrgContext ctx = orgAccess.require(slug);
        if (ctx.isGuest()) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    /** Task reports: also needs the Projects module switched on. */
    private OrgContext taskContext(String slug) {
        OrgContext ctx = reportContext(slug);
        modules.require(ctx.orgId(), Module.PROJECTS);
        return ctx;
    }

    /** The visible projects, or just {@code key} (NOT_FOUND when it isn't one of them). */
    private Map<UUID, Project> projects(OrgContext ctx, String key) {
        if (key == null || key.isBlank()) {
            return tasks.visibleProjects(ctx);
        }
        Project p = projectAccess.view(ctx, key).project();
        return Map.of(p.getId(), p);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(CalendarService.ZONE));
    }

    private static Map<LocalDate, Long> weeks(List<ReportRepository.WeekCount> counts) {
        return counts.stream().collect(Collectors.toMap(ReportRepository.WeekCount::weekStart,
                ReportRepository.WeekCount::count));
    }

    private static long sum(Map<?, Long> m) {
        return m.values().stream().mapToLong(Long::longValue).sum();
    }

    private static ApprovalStats stats(ApprovalRow r, long stale) {
        if (r == null) {
            return new ApprovalStats(0, 0, 0, 0, 0, 0, null, null, stale);
        }
        return new ApprovalStats(r.submitted(), r.approved(), r.rejected(), r.changesRequested(), r.pending(),
                r.withdrawn(), round(r.medianHours()), round(r.p90Hours()), stale);
    }

    private static Double round(Double hours) {
        return hours == null ? null : Math.round(hours * 10) / 10.0;
    }
}
