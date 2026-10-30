package com.businessplatform.workflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.workflow.domain.ApprovalAction;
import com.businessplatform.workflow.domain.ApprovalDelegation;
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
        /**
         * Open requests whose current step waits on {@code userId}, on someone they act for ({@code forUsers}), on
         * one of {@code roles}, or (for Admins: {@code escalated}) on anyone after escalation.
         */
        @Query("SELECT DISTINCT r FROM ApprovalRequest r, ApprovalStep s WHERE s.requestId = r.id AND r.orgId = :orgId"
                + " AND r.status = 'PENDING' AND s.stepOrder = r.currentStep AND s.status = 'PENDING'"
                + " AND (s.approverId = :userId OR s.approverId IN :forUsers OR s.approverRole IN :roles"
                + " OR (:escalated = true AND s.escalatedAt IS NOT NULL)) ORDER BY r.updatedAt ASC")
        List<ApprovalRequest> inbox(UUID orgId, UUID userId, List<UUID> forUsers, List<String> roles, boolean escalated);

        /** Removes the requests about records that are gone (their steps and actions go with them). */
        @Modifying
        @Query("DELETE FROM ApprovalRequest r WHERE r.orgId = :orgId AND r.moduleKey = :moduleKey"
                + " AND r.recordType = :recordType AND r.recordId IN :recordIds")
        int deleteForRecords(UUID orgId, String moduleKey, String recordType, java.util.Collection<UUID> recordIds);
    }

    public interface Steps extends JpaRepository<ApprovalStep, UUID> {

        List<ApprovalStep> findByRequestIdOrderByStepOrder(UUID requestId);

        /** Every step waiting for a decision now (for reminders and escalation). */
        @Query("SELECT s FROM ApprovalStep s, ApprovalRequest r WHERE s.requestId = r.id AND r.status = 'PENDING'"
                + " AND s.stepOrder = r.currentStep AND s.status = 'PENDING' AND s.activatedAt IS NOT NULL")
        List<ApprovalStep> waiting();
    }

    public interface Actions extends JpaRepository<ApprovalAction, UUID> {

        List<ApprovalAction> findByRequestIdOrderByCreatedAt(UUID requestId);
    }

    public interface Delegations extends JpaRepository<ApprovalDelegation, UUID> {

        List<ApprovalDelegation> findByOrgIdAndRevokedAtIsNullOrderByStartsOn(UUID orgId);

        List<ApprovalDelegation> findByOrgIdAndUserIdAndRevokedAtIsNull(UUID orgId, UUID userId);

        List<ApprovalDelegation> findByOrgIdAndDelegateIdAndRevokedAtIsNull(UUID orgId, UUID delegateId);

        Optional<ApprovalDelegation> findByIdAndOrgId(UUID id, UUID orgId);
    }
}
