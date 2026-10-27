package com.businessplatform.overview;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.Activity;
import com.businessplatform.activity.ActivityService;
import com.businessplatform.calendar.CalendarService;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.project.domain.Project;
import com.businessplatform.project.domain.Task;
import com.businessplatform.project.payload.ProjectPayloads.ActivityResponse;
import com.businessplatform.project.payload.ProjectPayloads.PersonRef;
import com.businessplatform.project.payload.ProjectPayloads.TaskResponse;
import com.businessplatform.project.repository.TaskRepository;
import com.businessplatform.project.service.People;
import com.businessplatform.project.service.ProjectService;
import com.businessplatform.project.service.TaskService;
import com.businessplatform.workflow.payload.WorkflowPayloads.ApprovalSummary;
import com.businessplatform.workflow.service.ApprovalService;

import lombok.RequiredArgsConstructor;

/**
 * The Overview dashboard in one call: my open tasks, my deadlines, project progress, approvals waiting
 * on me, upcoming events and recent activity — each already limited to what I may see.
 */
@Service
@RequiredArgsConstructor
public class OverviewService {

    private final OrgAccess access;
    private final ModuleService modules;
    private final TaskService tasks;
    private final TaskRepository taskRepo;
    private final ProjectService projects;
    private final ApprovalService approvals;
    private final CalendarService calendar;
    private final ActivityService activity;
    private final People people;
    private final java.time.Clock clock;

    public record ProjectProgress(String key, String name, String status, int progress, long open, long total,
            LocalDate dueDate) {
    }

    public record Overview(int openTasks, int overdueTasks, int dueThisWeek, int pendingApprovals,
            List<TaskResponse> myTasks, List<TaskResponse> deadlines, List<ProjectProgress> projects,
            List<ApprovalSummary> approvals, List<CalendarService.EventView> events, List<ActivityResponse> activity,
            boolean projectsEnabled) {
    }

    @Transactional(readOnly = true)
    public Overview get(String slug) {
        OrgContext ctx = access.require(slug);
        boolean on = modules.enabled(ctx.orgId()).contains(Module.PROJECTS);
        LocalDate today = LocalDate.now(clock.withZone(CalendarService.ZONE));
        List<TaskResponse> mine = on ? tasks.myTasks(slug) : List.of();
        List<TaskResponse> open = mine.stream().filter(t -> !Task.DONE.equals(t.status())).toList();
        int overdue = (int) open.stream().filter(t -> t.dueDate() != null && t.dueDate().isBefore(today)).count();
        int week = (int) open.stream().filter(t -> t.dueDate() != null && !t.dueDate().isBefore(today)
                && !t.dueDate().isAfter(today.plusDays(7))).count();
        List<TaskResponse> deadlines = open.stream().filter(t -> t.dueDate() != null)
                .sorted(Comparator.comparing(TaskResponse::dueDate)).limit(8).toList();
        List<ProjectProgress> progress = on ? projects.list(slug).stream()
                .filter(p -> "ACTIVE".equals(p.status()))
                .map(p -> {
                    long total = p.taskCounts().values().stream().mapToLong(Long::longValue).sum();
                    long done = p.taskCounts().getOrDefault(Task.DONE, 0L);
                    return new ProjectProgress(p.key(), p.name(), p.status(), p.progress(), total - done, total, p.dueDate());
                })
                .sorted(Comparator.comparing(ProjectProgress::open).reversed()).limit(6).toList() : List.of();
        List<ApprovalSummary> waiting = approvals.list(slug, "inbox");
        return new Overview(open.size(), overdue, week, waiting.size(), open.stream().limit(8).toList(), deadlines,
                progress, waiting.stream().limit(5).toList(), calendar.upcoming(ctx, 7).stream().limit(6).toList(),
                on ? recent(ctx) : List.of(), on);
    }

    private List<ActivityResponse> recent(OrgContext ctx) {
        Map<UUID, Project> visible = tasks.visibleProjects(ctx);
        if (visible.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>(visible.keySet());
        for (UUID pid : visible.keySet()) {
            taskRepo.findByProjectIdAndParentIdIsNullOrderByStatusAscPositionAsc(pid).forEach(t -> ids.add(t.getId()));
        }
        List<Activity> as = activity.recentFor(ctx.orgId(), ids, 12);
        Map<UUID, PersonRef> ps = people.of(as.stream().map(Activity::getActorId).toList());
        return as.stream().map(a -> new ActivityResponse(a.getId(), ps.get(a.getActorId()), a.getVerb(), a.getData(),
                a.getCreatedAt())).toList();
    }
}
