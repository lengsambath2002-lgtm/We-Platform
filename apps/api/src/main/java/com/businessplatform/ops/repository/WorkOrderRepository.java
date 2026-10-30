package com.businessplatform.ops.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.ops.domain.WorkOrder;

import jakarta.persistence.LockModeType;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID>, JpaSpecificationExecutor<WorkOrder> {

    Optional<WorkOrder> findByIdAndOrgId(UUID id, UUID orgId);

    /** The work order, locked until the transaction ends (status changes one at a time). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WorkOrder w WHERE w.id = :id AND w.orgId = :orgId")
    Optional<WorkOrder> lock(UUID id, UUID orgId);

    List<WorkOrder> findByOrgIdAndTicketIdOrderByCreatedAt(UUID orgId, UUID ticketId);

    List<WorkOrder> findTop50ByOrgIdAndAssetIdOrderByCreatedAtDesc(UUID orgId, UUID assetId);

    List<WorkOrder> findTop20ByOrgIdAndCustomerIdOrderByCreatedAtDesc(UUID orgId, UUID customerId);

    boolean existsByPlanIdAndPlanDue(UUID planId, LocalDate planDue);

    /** Work orders (not drafts or cancelled) scheduled to start in {@code from}–{@code to}; {@code tech}: only theirs. */
    @Query("SELECT w FROM WorkOrder w WHERE w.orgId = :orgId AND w.status IN ('SCHEDULED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED')"
            + " AND w.scheduledStart >= :from AND w.scheduledStart < :to AND (:tech IS NULL OR EXISTS (SELECT 1 FROM"
            + " WoTechnician t WHERE t.workOrderId = w.id AND t.userId = :tech)) ORDER BY w.scheduledStart")
    List<WorkOrder> scheduledBetween(UUID orgId, Instant from, Instant to, UUID tech);

    /** Drafts waiting for an approval decision. */
    @Query("SELECT COUNT(w) FROM WorkOrder w WHERE w.orgId = :orgId AND w.status = 'DRAFT' AND w.approvalState = 'PENDING'")
    long awaitingApproval(UUID orgId);

    /** Work orders completed in {@code from}–{@code to} (for the reports). */
    List<WorkOrder> findByOrgIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(UUID orgId, String status,
            Instant from, Instant to);
}
