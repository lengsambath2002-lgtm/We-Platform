package com.businessplatform.project.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.RecordLabeler;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.project.domain.Project;
import com.businessplatform.project.domain.Task;
import com.businessplatform.project.repository.ProjectRepository;
import com.businessplatform.project.repository.TaskRepository;

import lombok.RequiredArgsConstructor;

/** Names and links of projects and tasks for the audit log. */
@Component
@RequiredArgsConstructor
public class ProjectRecordLabeler implements RecordLabeler {

    private final ProjectRepository projects;
    private final TaskRepository tasks;

    @Override
    public boolean handles(String recordType) {
        return "project".equals(recordType) || TaskService.RECORD.equals(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Label> labels(OrgContext ctx, String recordType, Collection<UUID> ids) {
        Map<UUID, Label> out = new HashMap<>();
        if ("project".equals(recordType)) {
            projects.findAllById(ids).stream().filter(p -> p.getOrgId().equals(ctx.orgId())).forEach(p -> out.put(p.getId(),
                    new Label(p.getKey() + " " + p.getName(), "/" + ctx.slug() + "/projects/" + p.getKey())));
            return out;
        }
        var found = tasks.findAllById(ids).stream().filter(t -> t.getOrgId().equals(ctx.orgId())).toList();
        Map<UUID, Project> ps = projects.findAllById(found.stream().map(Task::getProjectId).distinct().toList()).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));
        for (Task t : found) {
            Project p = ps.get(t.getProjectId());
            out.put(t.getId(), new Label(p.getKey() + "-" + t.getNumber() + " " + t.getTitle(),
                    "/" + ctx.slug() + "/tasks/" + t.getId()));
        }
        return out;
    }
}
