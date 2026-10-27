package com.businessplatform.workflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.workflow.domain.ApprovalAction;
import com.businessplatform.workflow.domain.ApprovalRequest;
import com.businessplatform.workflow.domain.ApprovalStep;
import com.businessplatform.workflow.domain.WorkflowDefinition;
import com.businessplatform.workflow.domain.WorkflowStepDef;

import jakarta.persistence.LockModeType;

/** Repositories of the workflow engine. */
public final class WorkflowRepositories {

    private WorkflowRepositories() {
    }

    public interface Definitions extends JpaRepository<WorkflowDefinition, UUID> {

        List<WorkflowDefinition> findByOrgIdOrderByName(UUID orgId);

        Optional<WorkflowDefinition> findByIdAndOrgId(UUID id, UUID orgId);

        boolean existsByOrgIdAndName(UUID orgId, String name);

        long countByOrgId(UUID orgId);
    }

    public interface StepDefs extends JpaRepository<WorkflowStepDef, UUID> {

        List<WorkflowStepDef> findByWorkflowIdOrderByStepOrder(UUID workflowId);

        @Modifying
        @Query("DELETE FROM WorkflowStepDef s WHERE s.workflowId = :workflowId")
        void deleteByWorkflowId(UUID workflowId);
    }

    public interface Requests extends JpaRepository<ApprovalRequest, UUID> {

        Optional<ApprovalRequest> findByIdAndOrgId(UUID id, UUID orgId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT r FROM ApprovalRequest r WHERE r.id = :id AND r.orgId = :orgId")
        Optional<ApprovalRequest> lock(UUID id, UUID orgId);

        List<ApprovalRequest> findByOrgIdAndModuleKeyAndRecordTypeAndRecordIdOrderByCreatedAtDesc(UUID orgId,
                String moduleKey, String recordType, UUID recordId);

        List<ApprovalRequest> findByOrgIdAndRequesterIdOrderByUpdatedAtDesc(UUID orgId, UUID requesterId);

        List<ApprovalRequest> findTop200ByOrgIdOrderByUpdatedAtDesc(UUID orgId);

        /** Open requests whose current step waits on {@code userId} or on one of {@code roles}. */
        @Query("SELECT r FROM ApprovalRequest r, ApprovalStep s WHERE s.requestId = r.id AND r.orgId = :orgId"
                + " AND r.status = 'PENDING' AND s.stepOrder = r.currentStep AND s.status = 'PENDING'"
                + " AND (s.approverId = :userId OR s.approverRole IN :roles) ORDER BY r.updatedAt ASC")
        List<ApprovalRequest> inbox(UUID orgId, UUID userId, List<String> roles);
    }

    public interface Steps extends JpaRepository<ApprovalStep, UUID> {

        List<ApprovalStep> findByRequestIdOrderByStepOrder(UUID requestId);
    }

    public interface Actions extends JpaRepository<ApprovalAction, UUID> {

        List<ApprovalAction> findByRequestIdOrderByCreatedAt(UUID requestId);
    }
}
