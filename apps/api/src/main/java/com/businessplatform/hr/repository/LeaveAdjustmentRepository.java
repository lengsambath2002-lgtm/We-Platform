package com.businessplatform.hr.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.hr.domain.LeaveAdjustment;

public interface LeaveAdjustmentRepository extends JpaRepository<LeaveAdjustment, UUID> {

    List<LeaveAdjustment> findByOrgIdAndUserIdAndYearOrderByCreatedAtDesc(UUID orgId, UUID userId, short year);

    /** [leaveTypeId, sum of days] for one person and year. */
    @Query("SELECT a.leaveTypeId, SUM(a.days) FROM LeaveAdjustment a WHERE a.orgId = :orgId AND a.userId = :userId"
            + " AND a.year = :year GROUP BY a.leaveTypeId")
    List<Object[]> totals(UUID orgId, UUID userId, short year);
}
