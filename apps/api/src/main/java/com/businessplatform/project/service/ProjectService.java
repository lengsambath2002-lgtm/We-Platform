package com.businessplatform.project.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.module.domain.Module;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.project.domain.Project;
import com.businessplatform.project.domain.ProjectMember;
import com.businessplatform.project.domain.ProjectRole;
import com.businessplatform.project.payload.ProjectPayloads.*;
import com.businessplatform.project.repository.ProjectMemberRepository;
import com.businessplatform.project.repository.ProjectRepository;
import com.businessplatform.project.repository.TaskRepository;

import lombok.RequiredArgsConstructor;

/** Projects and their members. */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectAccess access;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final TaskRepository tasks;
    private final MembershipRepository memberships;
    private final People people;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    @Transactional
    public ProjectResponse create(String slug, CreateProjectRequest in) {
        OrgContext ctx = access.org(slug);
        if (ctx.isGuest()) {
            throw BusinessException.forbidden();
        }
        if (projects.existsByOrgIdAndKey(ctx.orgId(), in.key())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "key", "Another project uses this key");
        }
        checkDates(in.startDate(), in.dueDate());
        Project p = new Project();
        p.setId(Ids.next());
        p.setOrgId(ctx.orgId());
        p.setKey(in.key());
        p.setName(in.name().strip());
        p.setDescription(blankToNull(in.description()));
        p.setVisibility(in.visibility() == null ? "ORG" : in.visibility());
        p.setStartDate(in.startDate());
        p.setDueDate(in.dueDate());
        p.setLeadId(ctx.userId());
        p.setCreatedBy(ctx.userId());
        projects.saveAndFlush(p);
        Map<UUID, ProjectRole> wanted = new HashMap<>();
        if (in.members() != null) {
            in.members().forEach(m -> wanted.put(m.userId(), m.role()));
        }
        wanted.put(ctx.userId(), ProjectRole.LEAD);
        saveMembers(ctx, p, wanted);
        activity.record(ctx.orgId(), Module.PROJECTS, "project", p.getId(), ctx.userId(), "project.created",
                Map.of("name", p.getName(), "key", p.getKey()));
        notifications.send(ctx.orgId(), wanted.keySet(), ctx.userId(), "project.added",
                "You were added to " + p.getName(), null, "/" + ctx.slug() + "/projects/" + p.getKey());
        return toResponse(ctx, access.check(ctx, p).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(String slug) {
        OrgContext ctx = access.org(slug);
        List<ProjectAccess.Access> visible = projects.findByOrgIdOrderByName(ctx.orgId()).stream()
                .map(p -> access.check(ctx, p)).flatMap(Optional::stream).toList();
        Map<UUID, Map<String, Long>> counts = counts(visible.stream().map(a -> a.project().getId()).toList());
        Map<UUID, PersonRef> leads = people.of(visible.stream().map(a -> a.project().getLeadId()).toList());
        return visible.stream().map(a -> toResponse(a, counts.getOrDefault(a.project().getId(), Map.of()), leads))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(String slug, String key) {
        OrgContext ctx = access.org(slug);
        return toResponse(ctx, access.view(ctx, key));
    }

    @Transactional
    public ProjectResponse update(String slug, String key, UpdateProjectRequest in) {
        OrgContext ctx = access.org(slug);
        Project p = access.requireManage(ctx, key).project();
        Map<String, Object> changed = new HashMap<>();
        if (in.name() != null && !in.name().strip().equals(p.getName())) {
            changed.put("name", in.name().strip());
            p.setName(in.name().strip());
        }
        if (in.description() != null) {
            p.setDescription(blankToNull(in.description()));
            changed.put("description", true);
        }
        if (in.status() != null && !in.status().equals(p.getStatus())) {
            changed.put("status", in.status());
            p.setStatus(in.status());
        }
        if (in.visibility() != null && !in.visibility().equals(p.getVisibility())) {
            changed.put("visibility", in.visibility());
            p.setVisibility(in.visibility());
        }
        if (Boolean.TRUE.equals(in.clearDates())) {
            p.setStartDate(null);
            p.setDueDate(null);
            changed.put("dates", "cleared");
        } else if (in.startDate() != null || in.dueDate() != null) {
            var start = in.startDate() != null ? in.startDate() : p.getStartDate();
            var due = in.dueDate() != null ? in.dueDate() : p.getDueDate();
            checkDates(start, due);
            p.setStartDate(start);
            p.setDueDate(due);
            changed.put("dates", true);
        }
        if (!changed.isEmpty()) {
            activity.record(ctx.orgId(), Module.PROJECTS, "project", p.getId(), ctx.userId(), "project.updated", changed);
        }
        return toResponse(ctx, access.check(ctx, p).orElseThrow());
    }

    @Transactional
    public void delete(String slug, String key) {
        OrgContext ctx = access.org(slug);
        if (!ctx.isAdmin()) {
            throw BusinessException.forbidden();
        }
        projects.delete(access.view(ctx, key).project());
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> members(String slug, String key) {
        OrgContext ctx = access.org(slug);
        Project p = access.view(ctx, key).project();
        List<ProjectMember> ms = members.findByProjectId(p.getId());
        Map<UUID, PersonRef> ps = people.of(ms.stream().map(ProjectMember::getUserId).toList());
        return ms.stream().map(m -> new ProjectMemberResponse(ps.get(m.getUserId()), m.getRole()))
                .sorted((a, b) -> a.role() != b.role() ? a.role().compareTo(b.role())
                        : a.person().name().compareToIgnoreCase(b.person().name()))
                .toList();
    }

    /** Replaces the member list; there is always at least one Lead. */
    @Transactional
    public List<ProjectMemberResponse> setMembers(String slug, String key, List<MemberInput> in) {
        OrgContext ctx = access.org(slug);
        Project p = access.requireManage(ctx, key).project();
        Map<UUID, ProjectRole> wanted = new HashMap<>();
        in.forEach(m -> wanted.put(m.userId(), m.role()));
        if (!wanted.containsValue(ProjectRole.LEAD)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "members", "A project needs a lead");
        }
        Set<UUID> before = members.findByProjectId(p.getId()).stream().map(ProjectMember::getUserId)
                .collect(Collectors.toSet());
        saveMembers(ctx, p, wanted);
        if (!wanted.containsKey(p.getLeadId()) || wanted.get(p.getLeadId()) != ProjectRole.LEAD) {
            p.setLeadId(wanted.entrySet().stream().filter(e -> e.getValue() == ProjectRole.LEAD).findFirst()
                    .orElseThrow().getKey());
        }
        List<UUID> added = wanted.keySet().stream().filter(u -> !before.contains(u)).toList();
        notifications.send(ctx.orgId(), added, ctx.userId(), "project.added", "You were added to " + p.getName(), null,
                "/" + ctx.slug() + "/projects/" + p.getKey());
        activity.record(ctx.orgId(), Module.PROJECTS, "project", p.getId(), ctx.userId(), "project.members",
                Map.of("count", wanted.size()));
        return members(slug, key);
    }

    /** Makes the project's members exactly {@code wanted}: updates kept rows, deletes removed ones, inserts new ones. */
    private void saveMembers(OrgContext ctx, Project p, Map<UUID, ProjectRole> wanted) {
        Map<UUID, Membership> active = memberships.findByOrgIdOrderByCreatedAt(ctx.orgId()).stream()
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()))
                .collect(Collectors.toMap(Membership::getUserId, m -> m));
        Map<UUID, ProjectMember> existing = members.findByProjectId(p.getId()).stream()
                .collect(Collectors.toMap(ProjectMember::getUserId, m -> m));
        List<ProjectMember> rows = new ArrayList<>();
        wanted.forEach((userId, role) -> {
            Membership m = active.get(userId);
            if (m == null) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "members", "Not a member of the organisation");
            }
            if (m.getRole() == OrgRole.GUEST && role == ProjectRole.LEAD) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "members", "A guest can't lead a project");
            }
            ProjectMember pm = existing.get(userId);
            if (pm == null) {
                pm = new ProjectMember();
                pm.setProjectId(p.getId());
                pm.setUserId(userId);
                pm.setAddedAt(clock.instant());
            }
            pm.setRole(role);
            rows.add(pm);
        });
        members.deleteAll(existing.values().stream().filter(m -> !wanted.containsKey(m.getUserId())).toList());
        members.saveAll(rows);
    }

    Map<UUID, Map<String, Long>> counts(List<UUID> projectIds) {
        Map<UUID, Map<String, Long>> out = new HashMap<>();
        if (projectIds.isEmpty()) {
            return out;
        }
        for (Object[] r : tasks.countsByStatus(projectIds)) {
            out.computeIfAbsent((UUID) r[0], k -> new HashMap<>()).put((String) r[1], (Long) r[2]);
        }
        return out;
    }

    private ProjectResponse toResponse(OrgContext ctx, ProjectAccess.Access a) {
        Map<UUID, Map<String, Long>> c = counts(List.of(a.project().getId()));
        return toResponse(a, c.getOrDefault(a.project().getId(), Map.of()), people.of(List.of(
                a.project().getLeadId() == null ? ctx.userId() : a.project().getLeadId())));
    }

    private static ProjectResponse toResponse(ProjectAccess.Access a, Map<String, Long> counts,
            Map<UUID, PersonRef> people) {
        Project p = a.project();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        int progress = total == 0 ? 0 : (int) Math.round(100.0 * counts.getOrDefault("DONE", 0L) / total);
        return new ProjectResponse(p.getId(), p.getKey(), p.getName(), p.getDescription(), p.getStatus(),
                p.getVisibility(), p.getLeadId() == null ? null : people.get(p.getLeadId()), p.getStartDate(),
                p.getDueDate(), counts, progress, a.role(), a.canEdit(), a.canManage(), p.getUpdatedAt());
    }

    static void checkDates(java.time.LocalDate start, java.time.LocalDate due) {
        if (start != null && due != null && due.isBefore(start)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "dueDate", "The due date is before the start");
        }
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
