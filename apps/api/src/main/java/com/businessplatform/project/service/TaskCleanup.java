package com.businessplatform.project.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.businessplatform.file.FileService;
import com.businessplatform.module.domain.Module;
import com.businessplatform.project.repository.TaskRepository;
import com.businessplatform.workflow.service.ApprovalCleanup;

import lombok.RequiredArgsConstructor;

/**
 * What goes with tasks deleted for good: the database removes their subtasks, comments and attachment
 * links; this removes the approval requests about them and the uploaded files (rows and bytes).
 * Their activity stays: the audit log is append-only.
 */
@Component
@RequiredArgsConstructor
class TaskCleanup {

    private final TaskRepository tasks;
    private final ApprovalCleanup approvals;
    private final FileService files;

    /** A task and its subtasks. */
    void task(UUID orgId, UUID taskId) {
        List<UUID> ids = new ArrayList<>(tasks.subtaskIds(taskId));
        ids.add(taskId);
        purge(orgId, ids);
    }

    /** Every task of a project. */
    void project(UUID orgId, UUID projectId) {
        purge(orgId, tasks.idsInProject(projectId));
    }

    private void purge(UUID orgId, Collection<UUID> taskIds) {
        if (taskIds.isEmpty()) {
            return;
        }
        approvals.recordsDeleted(orgId, Module.PROJECTS, TaskService.RECORD, taskIds);
        files.discard(orgId, tasks.attachmentIdsOf(taskIds));
    }
}
