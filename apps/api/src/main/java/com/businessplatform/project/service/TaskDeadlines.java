package com.businessplatform.project.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.calendar.DeadlineSource;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.project.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;

/** Task due dates on the calendar (only projects the person can see; nothing when the module is off). */
@Component
@RequiredArgsConstructor
public class TaskDeadlines implements DeadlineSource {

    private final TaskService tasks;
    private final ModuleService modules;
    private final ProjectRepository projects;
    private final ProjectAccess access;

    @Override
    @Transactional(readOnly = true)
    public List<Deadline> deadlines(OrgContext ctx, LocalDate from, LocalDate to, boolean mineOnly) {
        if (!modules.enabled(ctx.orgId()).contains(Module.PROJECTS)) {
            return List.of();
        }
        return tasks.due(ctx, from, to, mineOnly).stream()
                .map(t -> new Deadline(Module.PROJECTS, t.id(), t.key(), t.title(), t.dueDate(), t.status(),
                        "/" + ctx.slug() + "/tasks/" + t.id(),
                        t.assignee() != null && t.assignee().id().equals(ctx.userId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canSeeProject(OrgContext ctx, UUID projectId) {
        return projects.findById(projectId).filter(p -> p.getOrgId().equals(ctx.orgId()))
                .flatMap(p -> access.check(ctx, p)).isPresent();
    }
}
