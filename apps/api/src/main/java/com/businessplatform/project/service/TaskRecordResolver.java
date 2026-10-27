package com.businessplatform.project.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.module.domain.Module;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.project.domain.Project;
import com.businessplatform.project.repository.ProjectRepository;
import com.businessplatform.project.repository.TaskRepository;
import com.businessplatform.workflow.service.RecordResolver;

import lombok.RequiredArgsConstructor;

/** Lets the approval engine check tasks: who may see one, who may request approval, its project lead. */
@Component
@RequiredArgsConstructor
public class TaskRecordResolver implements RecordResolver {

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final ProjectAccess access;

    @Override
    public String module() {
        return Module.PROJECTS;
    }

    @Override
    public boolean handles(String recordType) {
        return TaskService.RECORD.equals(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordView> view(OrgContext ctx, String recordType, UUID recordId) {
        return tasks.findByIdAndOrgId(recordId, ctx.orgId()).flatMap(t -> {
            Project p = projects.findById(t.getProjectId()).orElseThrow();
            return access.check(ctx, p).map(a -> new RecordView(p.getKey() + "-" + t.getNumber() + " " + t.getTitle(),
                    "/" + ctx.slug() + "/tasks/" + t.getId(), a.canEdit()));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> leadOf(OrgContext ctx, String recordType, UUID recordId) {
        return tasks.findByIdAndOrgId(recordId, ctx.orgId())
                .flatMap(t -> projects.findById(t.getProjectId())).map(Project::getLeadId);
    }
}
