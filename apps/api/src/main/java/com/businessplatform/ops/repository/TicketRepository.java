package com.businessplatform.ops.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.ops.domain.Ticket;

import jakarta.persistence.LockModeType;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findByIdAndOrgId(UUID id, UUID orgId);

    /** The ticket, locked until the transaction ends (status changes one at a time). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.id = :id AND t.orgId = :orgId")
    Optional<Ticket> lock(UUID id, UUID orgId);

    /** [status, count] of tickets, optionally only those assigned to {@code assignee}. */
    @Query("SELECT t.status, COUNT(t) FROM Ticket t WHERE t.orgId = :orgId AND (:assignee IS NULL OR t.assigneeId = :assignee)"
            + " GROUP BY t.status")
    List<Object[]> countByStatus(UUID orgId, UUID assignee);

    /** Open tickets past a target. */
    @Query("SELECT t FROM Ticket t WHERE t.orgId = :orgId AND t.status IN ('NEW', 'TRIAGED', 'IN_PROGRESS', 'WAITING')"
            + " AND (:assignee IS NULL OR t.assigneeId = :assignee)"
            + " AND ((t.respondedAt IS NULL AND t.responseDueAt < :now) OR t.resolutionDueAt < :now)"
            + " ORDER BY t.resolutionDueAt")
    List<Ticket> breached(UUID orgId, UUID assignee, Instant now);

    /** Open tickets past 75% of a window but not breached. */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.orgId = :orgId AND t.status IN ('NEW', 'TRIAGED', 'IN_PROGRESS', 'WAITING')"
            + " AND (:assignee IS NULL OR t.assigneeId = :assignee)"
            + " AND NOT ((t.respondedAt IS NULL AND t.responseDueAt < :now) OR t.resolutionDueAt < :now)"
            + " AND ((t.respondedAt IS NULL AND t.responseWarnAt <= :now) OR t.resolutionWarnAt <= :now)")
    long atRisk(UUID orgId, UUID assignee, Instant now);

    /** [week start, opened] of tickets created in {@code from}–{@code to} (weeks start on Monday, Phnom Penh time). */
    @Query(value = "SELECT CAST(date_trunc('week', created_at AT TIME ZONE 'Asia/Phnom_Penh') AS DATE), COUNT(*)"
            + " FROM svc_ticket WHERE org_id = :orgId AND created_at >= :from AND created_at < :to GROUP BY 1", nativeQuery = true)
    List<Object[]> openedPerWeek(UUID orgId, Instant from, Instant to);

    @Query(value = "SELECT CAST(date_trunc('week', resolved_at AT TIME ZONE 'Asia/Phnom_Penh') AS DATE), COUNT(*)"
            + " FROM svc_ticket WHERE org_id = :orgId AND resolved_at >= :from AND resolved_at < :to GROUP BY 1", nativeQuery = true)
    List<Object[]> resolvedPerWeek(UUID orgId, Instant from, Instant to);

    /** Tickets created in {@code from}–{@code to} (for the reports). */
    List<Ticket> findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(UUID orgId, Instant from, Instant to);

    List<Ticket> findTop20ByOrgIdAndCustomerIdOrderByCreatedAtDesc(UUID orgId, UUID customerId);

    List<Ticket> findTop20ByOrgIdAndAssetIdOrderByCreatedAtDesc(UUID orgId, UUID assetId);
}
