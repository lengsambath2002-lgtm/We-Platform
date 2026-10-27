package com.businessplatform.project.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.businessplatform.activity.Activity;
import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.file.FileService;
import com.businessplatform.file.StoredFile;
import com.businessplatform.file.StoredFileRepository;
import com.businessplatform.module.domain.Module;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.project.domain.Project;
import com.businessplatform.project.domain.ProjectMember;
import com.businessplatform.project.domain.ProjectRole;
import com.businessplatform.project.domain.Task;
import com.businessplatform.project.domain.TaskComment;
import com.businessplatform.project.payload.ProjectPayloads.*;
import com.businessplatform.project.repository.ProjectMemberRepository;
import com.businessplatform.project.repository.ProjectRepository;
import com.businessplatform.project.repository.TaskCommentRepository;
import com.businessplatform.project.repository.TaskRepository;

import lombok.RequiredArgsConstructor;

/**
 * Tasks and subtasks: create, edit, move on the board, comment (with @mentions), attach files, and
 * their history. Every change is recorded in the activity log and notifies the people concerned.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    public static final String RECORD = "task";

    private final ProjectAccess access;
    private final ProjectRepository projects;
    private final ProjectMemberRepository projectMembers;
    private final TaskRepository tasks;
    private final TaskCommentRepository comments;
    private final MembershipRepository memberships;
    private final StoredFileRepository files;
    private final FileService fileService;
    private final People people;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    // ── Read ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TaskResponse> list(String slug, String projectKey) {
        OrgContext ctx = access.org(slug);
        Project p = access.view(ctx, projectKey).project();
        return toResponses(tasks.findByProjectIdAndParentIdIsNullOrderByStatusAscPositionAsc(p.getId()),
                Map.of(p.getId(), p));
    }

    @Transactional(readOnly = true)
    public TaskDetailResponse get(String slug, UUID id) {
        OrgContext ctx = access.org(slug);
        Task t = load(ctx, id);
        ProjectAccess.Access a = access.view(ctx, t.getProjectId());
        Map<UUID, Project> ps = Map.of(a.project().getId(), a.project());
        List<FileRef> attachments = files.findByIdInAndOrgId(tasks.attachmentIds(t.getId()), ctx.orgId()).stream()
                .map(f -> new FileRef(f.getId(), f.getName(), f.getContentType(), f.getSizeBytes(), f.getCreatedAt()))
                .toList();
        return new TaskDetailResponse(toResponses(List.of(t), ps).get(0),
                toResponses(tasks.findByParentIdOrderByNumber(t.getId()), ps), attachments, a.canEdit());
    }

    /** Tasks assigned to me in projects I can see, open first, soonest due first. */
    @Transactional(readOnly = true)
    public List<TaskResponse> myTasks(String slug) {
        OrgContext ctx = access.org(slug);
        Map<UUID, Project> visible = visibleProjects(ctx);
        if (visible.isEmpty()) {
            return List.of();
        }
        return toResponses(tasks.myTasks(ctx.orgId(), ctx.userId(), visible.keySet()), visible);
    }

    /** Tasks due between two dates in projects I can see ({@code mineOnly}: assigned to me). */
    @Transactional(readOnly = true)
    public List<TaskResponse> due(OrgContext ctx, LocalDate from, LocalDate to, boolean mineOnly) {
        Map<UUID, Project> visible = visibleProjects(ctx);
        if (visible.isEmpty()) {
            return List.of();
        }
        return toResponses(tasks.dueBetween(ctx.orgId(), visible.keySet(), from, to, mineOnly ? ctx.userId() : null),
                visible);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Project> visibleProjects(OrgContext ctx) {
        Map<UUID, Project> out = new LinkedHashMap<>();
        projects.findByOrgIdOrderByName(ctx.orgId()).forEach(p -> access.check(ctx, p).ifPresent(a -> out.put(p.getId(), p)));
        return out;
    }

    // ── Write ────────────────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse create(String slug, String projectKey, CreateTaskRequest in) {
        OrgContext ctx = access.org(slug);
        Project p = access.view(ctx, projectKey).project();
        access.requireEdit(ctx, p.getId());
        Task parent = null;
        if (in.parentId() != null) {
            parent = load(ctx, in.parentId());
            if (!parent.getProjectId().equals(p.getId()) || parent.getParentId() != null) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "parentId",
                        "A subtask belongs to a top-level task of the same project");
            }
        }
        ProjectService.checkDates(in.startDate(), in.dueDate());
        checkAssignee(ctx, p, in.assigneeId());
        Project locked = projects.lock(p.getId()).orElseThrow();
        Task t = new Task();
        t.setId(Ids.next());
        t.setOrgId(ctx.orgId());
        t.setProjectId(p.getId());
        t.setNumber(locked.getNextNumber());
        locked.setNextNumber(locked.getNextNumber() + 1);
        t.setParentId(parent == null ? null : parent.getId());
        t.setTitle(in.title().strip());
        t.setDescription(ProjectService.blankToNull(in.description()));
        t.setStatus(in.status() == null ? Task.TODO : in.status());
        t.setPriority(in.priority() == null ? "MEDIUM" : in.priority());
        t.setAssigneeId(in.assigneeId());
        t.setReporterId(ctx.userId());
        t.setStartDate(in.startDate());
        t.setDueDate(in.dueDate());
        t.setPosition(nextPosition(p.getId(), t.getStatus()));
        if (Task.DONE.equals(t.getStatus())) {
            t.setCompletedAt(clock.instant());
        }
        tasks.save(t);
        String key = p.getKey() + "-" + t.getNumber();
        activity.record(ctx.orgId(), Module.PROJECTS, RECORD, t.getId(), ctx.userId(), "task.created",
                Map.of("key", key, "title", t.getTitle()));
        if (parent != null) {
            activity.record(ctx.orgId(), Module.PROJECTS, RECORD, parent.getId(), ctx.userId(), "task.subtask.added",
                    Map.of("key", key, "title", t.getTitle()));
        }
        if (t.getAssigneeId() != null) {
            notifications.send(ctx.orgId(), List.of(t.getAssigneeId()), ctx.userId(), "task.assigned",
                    key + " " + t.getTitle(), "Assigned to you", link(ctx, t));
        }
        return toResponses(List.of(t), Map.of(p.getId(), p)).get(0);
    }

    @Transactional
    public TaskResponse update(String slug, UUID id, UpdateTaskRequest in) {
        OrgContext ctx = access.org(slug);
        Task t = load(ctx, id);
        Project p = access.requireEdit(ctx, t.getProjectId()).project();
        Set<String> clear = in.clear() == null ? Set.of() : Set.copyOf(in.clear());
        Map<String, Object> changed = new LinkedHashMap<>();
        UUID previousAssignee = t.getAssigneeId();
        if (in.title() != null && !in.title().strip().equals(t.getTitle())) {
            changed.put("title", Map.of("from", t.getTitle(), "to", in.title().strip()));
            t.setTitle(in.title().strip());
        }
        if (clear.contains("description")) {
            t.setDescription(null);
            changed.put("description", "cleared");
        } else if (in.description() != null && !Objects.equals(in.description().strip(), t.getDescription())) {
            t.setDescription(ProjectService.blankToNull(in.description()));
            changed.put("description", "edited");
        }
        if (in.priority() != null && !in.priority().equals(t.getPriority())) {
            changed.put("priority", Map.of("from", t.getPriority(), "to", in.priority()));
            t.setPriority(in.priority());
        }
        if (clear.contains("assignee")) {
            if (t.getAssigneeId() != null) {
                changed.put("assignee", Map.of("to", "none"));
            }
            t.setAssigneeId(null);
        } else if (in.assigneeId() != null && !in.assigneeId().equals(t.getAssigneeId())) {
            checkAssignee(ctx, p, in.assigneeId());
            changed.put("assignee", Map.of("to", in.assigneeId().toString()));
            t.setAssigneeId(in.assigneeId());
        }
        LocalDate start = clear.contains("startDate") ? null : in.startDate() != null ? in.startDate() : t.getStartDate();
        LocalDate due = clear.contains("dueDate") ? null : in.dueDate() != null ? in.dueDate() : t.getDueDate();
        ProjectService.checkDates(start, due);
        if (!Objects.equals(due, t.getDueDate())) {
            changed.put("dueDate", Map.of("to", due == null ? "none" : due.toString()));
        }
        if (!Objects.equals(start, t.getStartDate())) {
            changed.put("startDate", Map.of("to", start == null ? "none" : start.toString()));
        }
        t.setStartDate(start);
        t.setDueDate(due);
        if (in.status() != null && !in.status().equals(t.getStatus())) {
            changed.put("status", Map.of("from", t.getStatus(), "to", in.status()));
            setStatus(t, in.status());
            t.setPosition(nextPosition(p.getId(), in.status()));
        }
        if (!changed.isEmpty()) {
            activity.record(ctx.orgId(), Module.PROJECTS, RECORD, t.getId(), ctx.userId(), "task.updated", changed);
            notifyChange(ctx, p, t, changed, previousAssignee);
        }
        return toResponses(List.of(t), Map.of(p.getId(), p)).get(0);
    }

    /** Drops a task into a status column at {@code index}; the column is renumbered. */
    @Transactional
    public TaskResponse move(String slug, UUID id, MoveTaskRequest in) {
        OrgContext ctx = access.org(slug);
        Task t = load(ctx, id);
        if (t.getParentId() != null) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "id", "Subtasks aren't on the board");
        }
        Project p = access.requireEdit(ctx, t.getProjectId()).project();
        String from = t.getStatus();
        List<Task> column = new ArrayList<>(tasks.findByProjectIdAndStatusAndParentIdIsNullOrderByPositionAsc(p.getId(),
                in.status()));
        column.removeIf(x -> x.getId().equals(t.getId()));
        column.add(Math.max(0, Math.min(in.index(), column.size())), t);
        for (int i = 0; i < column.size(); i++) {
            column.get(i).setPosition(i + 1);
        }
        if (!from.equals(in.status())) {
            setStatus(t, in.status());
            Map<String, Object> changed = Map.of("status", Map.of("from", from, "to", in.status()));
            activity.record(ctx.orgId(), Module.PROJECTS, RECORD, t.getId(), ctx.userId(), "task.updated", changed);
            notifyChange(ctx, p, t, changed, t.getAssigneeId());
        }
        return toResponses(List.of(t), Map.of(p.getId(), p)).get(0);
    }

    @Transactional
    public void delete(String slug, UUID id) {
        OrgContext ctx = access.org(slug);
        Task t = load(ctx, id);
        ProjectAccess.Access a = access.requireEdit(ctx, t.getProjectId());
        if (!a.canManage() && !ctx.userId().equals(t.getReporterId())) {
            throw BusinessException.forbidden();
        }
        tasks.delete(t);
        activity.record(ctx.orgId(), Module.PROJECTS, "project", a.project().getId(), ctx.userId(), "task.deleted",
                Map.of("key", a.project().getKey() + "-" + t.getNumber(), "title", t.getTitle()));
    }

    // ── Comments ─────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CommentResponse> comments(String slug, UUID taskId) {
        OrgContext ctx = access.org(slug);
        Task t = load(ctx, taskId);
        access.view(ctx, t.getProjectId());
        List<TaskComment> cs = comments.findByTaskIdOrderByCreatedAt(t.getId());
        Map<UUID, PersonRef> ps = people.of(cs.stream().map(TaskComment::getAuthorId).toList());
        return cs.stream().map(c -> new CommentResponse(c.getId(), ps.get(c.getAuthorId()), c.getBody(),
                c.getCreatedAt(), c.getEditedAt())).toList();
    }

    /** Anyone who can see the project may comment (viewers included); mentions must be able to see it too. */
    @Transactional
    public CommentResponse comment(String slug, UUID taskId, CommentRequest in) {
        OrgContext ctx = access.org(slug);
        Task t = load(ctx, taskId);
        Project p = access.view(ctx, t.getProjectId()).project();
        TaskComment c = new TaskComment();
        c.setId(Ids.next());
        c.setOrgId(ctx.orgId());
        c.setTaskId(t.getId());
        c.setAuthorId(ctx.userId());
        c.setBody(in.body().strip());
        c.setCreatedAt(clock.instant());
        comments.save(c);
        activity.record(ctx.orgId(), Module.PROJECTS, RECORD, t.getId(), ctx.userId(), "task.commented",
                Map.of("excerpt", excerpt(c.getBody())));
        String key = p.getKey() + "-" + t.getNumber();
        Set<UUID> mentioned = new LinkedHashSet<>();
        if (in.mentionIds() != null) {
            for (UUID u : in.mentionIds()) {
                if (canSee(ctx, p, u)) {
                    mentioned.add(u);
                }
            }
        }
        notifications.send(ctx.orgId(), mentioned, ctx.userId(), "task.mentioned", key + " " + t.getTitle(),
                "Mentioned you: " + excerpt(c.getBody()), link(ctx, t));
        Set<UUID> watchers = new LinkedHashSet<>(List.of(t.getReporterId()));
        if (t.getAssigneeId() != null) {
            watchers.add(t.getAssigneeId());
        }
        watchers.removeAll(mentioned);
        notifications.send(ctx.orgId(), watchers, ctx.userId(), "task.commented", key + " " + t.getTitle(),
                "New comment: " + excerpt(c.getBody()), link(ctx, t));
        return new CommentResponse(c.getId(), people.of(List.of(ctx.userId())).get(ctx.userId()), c.getBody(),
                c.getCreatedAt(), null);
    }

    // ── Attachments ──────────────────────────────────────────────────────────────────

    @Transactional
    public FileRef attach(String slug, UUID taskId, MultipartFile upload) {
        OrgContext ctx = access.org(slug);
        Task t = load(ctx, taskId);
        access.requireEdit(ctx, t.getProjectId());
        StoredFile f = fileService.store(ctx.orgId(), ctx.userId(), upload);
        tasks.attach(t.getId(), f.getId());
        activity.record(ctx.orgId(), Module.PROJECTS, RECORD, t.getId(), ctx.userId(), "task.attached",
                Map.of("name", f.getName()));
        return new FileRef(f.getId(), f.getName(), f.getContentType(), f.getSizeBytes(), f.getCreatedAt());
    }

    @Transactional
    public void detach(String slug, UUID taskId, UUID fileId) {
        OrgContext ctx = access.org(slug);
        Task t = load(ctx, taskId);
        access.requireEdit(ctx, t.getProjectId());
        if (tasks.detach(t.getId(), fileId) == 0) {
            throw BusinessException.notFound();
        }
        activity.record(ctx.orgId(), Module.PROJECTS, RECORD, t.getId(), ctx.userId(), "task.detached",
                Map.of("fileId", fileId.toString()));
    }

    /** The file, if it is attached to a task the caller can see. */
    @Transactional(readOnly = true)
    public StoredFile attachment(String slug, UUID taskId, UUID fileId) {
        OrgContext ctx = access.org(slug);
        Task t = load(ctx, taskId);
        access.view(ctx, t.getProjectId());
        if (!tasks.attachmentIds(t.getId()).contains(fileId)) {
            throw BusinessException.notFound();
        }
        return files.findById(fileId).filter(f -> f.getOrgId().equals(ctx.orgId())).orElseThrow(BusinessException::notFound);
    }

    // ── History ──────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ActivityResponse> history(String slug, UUID taskId) {
        OrgContext ctx = access.org(slug);
        Task t = load(ctx, taskId);
        access.view(ctx, t.getProjectId());
        List<Activity> as = activity.forRecord(ctx.orgId(), RECORD, t.getId());
        Map<UUID, PersonRef> ps = people.of(as.stream().map(Activity::getActorId).toList());
        return as.stream().map(a -> new ActivityResponse(a.getId(), ps.get(a.getActorId()), a.getVerb(), a.getData(),
                a.getCreatedAt())).toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    /** The task in this organisation; NOT_FOUND otherwise (visibility is checked by the caller). */
    public Task load(OrgContext ctx, UUID id) {
        return tasks.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    /** May {@code userId} see project {@code p}? (an active org member who'd pass {@link ProjectAccess}). */
    boolean canSee(OrgContext ctx, Project p, UUID userId) {
        Optional<Membership> m = memberships.findByOrgIdAndUserId(ctx.orgId(), userId)
                .filter(x -> Membership.ACTIVE.equals(x.getStatus()));
        if (m.isEmpty()) {
            return false;
        }
        if (m.get().getRole().isAdmin()) {
            return true;
        }
        boolean member = projectMembers.findByProjectIdAndUserId(p.getId(), userId).isPresent();
        return member || ("ORG".equals(p.getVisibility()) && m.get().getRole() != OrgRole.GUEST);
    }

    private void checkAssignee(OrgContext ctx, Project p, UUID assigneeId) {
        if (assigneeId == null) {
            return;
        }
        if (!canSee(ctx, p, assigneeId)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "assigneeId", "This person can't see the project");
        }
        boolean viewer = projectMembers.findByProjectIdAndUserId(p.getId(), assigneeId)
                .map(ProjectMember::getRole).filter(r -> r == ProjectRole.VIEWER).isPresent();
        if (viewer) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "assigneeId", "Viewers can't be assigned tasks");
        }
    }

    private void setStatus(Task t, String status) {
        t.setStatus(status);
        t.setCompletedAt(Task.DONE.equals(status) ? clock.instant() : null);
    }

    private double nextPosition(UUID projectId, String status) {
        List<Task> col = tasks.findByProjectIdAndStatusAndParentIdIsNullOrderByPositionAsc(projectId, status);
        return col.isEmpty() ? 1 : col.get(col.size() - 1).getPosition() + 1;
    }

    private void notifyChange(OrgContext ctx, Project p, Task t, Map<String, Object> changed, UUID previousAssignee) {
        String key = p.getKey() + "-" + t.getNumber();
        if (changed.containsKey("assignee") && t.getAssigneeId() != null) {
            notifications.send(ctx.orgId(), List.of(t.getAssigneeId()), ctx.userId(), "task.assigned",
                    key + " " + t.getTitle(), "Assigned to you", link(ctx, t));
        }
        if (changed.containsKey("status")) {
            Set<UUID> to = new LinkedHashSet<>(List.of(t.getReporterId()));
            if (previousAssignee != null) {
                to.add(previousAssignee);
            }
            notifications.send(ctx.orgId(), to, ctx.userId(), "task.status", key + " " + t.getTitle(),
                    "Moved to " + t.getStatus().replace('_', ' ').toLowerCase(), link(ctx, t));
        }
    }

    private static String link(OrgContext ctx, Task t) {
        return "/" + ctx.slug() + "/tasks/" + t.getId();
    }

    private static String excerpt(String s) {
        String one = s.replaceAll("\\s+", " ");
        return one.length() > 140 ? one.substring(0, 140) + "…" : one;
    }

    List<TaskResponse> toResponses(List<Task> list, Map<UUID, Project> projectsById) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Task::getId).toList();
        Map<UUID, int[]> sub = new HashMap<>();
        for (Object[] r : tasks.subtaskCounts(ids)) {
            sub.put((UUID) r[0], new int[] { ((Number) r[1]).intValue(), ((Number) r[2]).intValue() });
        }
        Map<UUID, Integer> cc = new HashMap<>();
        for (Object[] r : tasks.commentCounts(ids)) {
            cc.put((UUID) r[0], ((Number) r[1]).intValue());
        }
        Collection<UUID> personIds = list.stream()
                .flatMap(t -> java.util.stream.Stream.of(t.getAssigneeId(), t.getReporterId())).toList();
        Map<UUID, PersonRef> ps = people.of(personIds);
        return list.stream().map(t -> {
            Project p = projectsById.get(t.getProjectId());
            int[] s = sub.getOrDefault(t.getId(), new int[] { 0, 0 });
            return new TaskResponse(t.getId(), p.getKey() + "-" + t.getNumber(), t.getNumber(), p.getId(), p.getKey(),
                    p.getName(), t.getParentId(), t.getTitle(), t.getDescription(), t.getStatus(), t.getPriority(),
                    t.getAssigneeId() == null ? null : ps.get(t.getAssigneeId()), ps.get(t.getReporterId()),
                    t.getStartDate(), t.getDueDate(), s[0], s[1], cc.getOrDefault(t.getId(), 0), t.getCompletedAt(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }).collect(Collectors.toList());
    }
}
