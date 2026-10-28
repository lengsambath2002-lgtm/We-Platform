package com.businessplatform.workflow.service;

import java.util.Collection;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.workflow.repository.WorkflowRepositories;

import lombok.RequiredArgsConstructor;

/**
 * Called by a module when it deletes records for good, so no approval request is left pointing at a
 * record that no longer exists (it could not be shown or decided any more).
 */
@Service
@RequiredArgsConstructor
public class ApprovalCleanup {

    private final WorkflowRepositories.Requests requests;

    @Transactional
    public int recordsDeleted(UUID orgId, String module, String recordType, Collection<UUID> recordIds) {
        if (recordIds.isEmpty()) {
            return 0;
        }
        return requests.deleteForRecords(orgId, module, recordType, recordIds);
    }
}
