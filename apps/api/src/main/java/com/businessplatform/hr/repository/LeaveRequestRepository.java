package com.businessplatform.hr.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.hr.domain.LeaveRequest;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID>, JpaSpecificationExecutor<LeaveRequest> {

    Optional<LeaveRequest> findByIdAndOrgId(UUID id, UUID orgId);

    List<LeaveRequest> findByOrgIdAndUserIdOrderByStartDateDesc(UUID orgId, UUID userId);

    /** Whether the person already has pending or approved leave on any day of {@code start}–{@code end}. */
    @Query("SELECT COUNT(l) > 0 FROM LeaveRequest l WHERE l.orgId = :orgId AND l.userId = :userId"
            + " AND l.status IN ('PENDING', 'APPROVED') AND l.startDate <= :end AND l.endDate >= :start")
    boolean overlaps(UUID orgId, UUID userId, LocalDate start, LocalDate end);

    /** [leaveTypeId, status, sum of days] of pending and approved leave starting in {@code from}–{@code to}. */
    @Query("SELECT l.leaveTypeId, l.status, SUM(l.days) FROM LeaveRequest l WHERE l.orgId = :orgId"
            + " AND l.userId = :userId AND l.status IN ('PENDING', 'APPROVED') AND l.startDate BETWEEN :from AND :to"
            + " GROUP BY l.leaveTypeId, l.status")
    List<Object[]> totals(UUID orgId, UUID userId, LocalDate from, LocalDate to);

    /** Approved leave touching {@code from}–{@code to}, earliest first. */
    @Query("SELECT l FROM LeaveRequest l WHERE l.orgId = :orgId AND l.status = 'APPROVED'"
            + " AND l.startDate <= :to AND l.endDate >= :from ORDER BY l.startDate, l.id")
    List<LeaveRequest> approvedBetween(UUID orgId, LocalDate from, LocalDate to);
}
