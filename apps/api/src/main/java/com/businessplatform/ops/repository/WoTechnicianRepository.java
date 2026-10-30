package com.businessplatform.ops.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.ops.domain.WoTechnician;

public interface WoTechnicianRepository extends JpaRepository<WoTechnician, WoTechnician.Key> {

    List<WoTechnician> findByWorkOrderIdIn(Collection<UUID> workOrderIds);

    boolean existsByWorkOrderIdAndUserId(UUID workOrderId, UUID userId);

    @Modifying
    @Query("DELETE FROM WoTechnician t WHERE t.workOrderId = :workOrderId")
    void deleteByWorkOrderId(UUID workOrderId);

    /** [userId, count] of open (scheduled, in progress, on hold) work orders per technician. */
    @Query("SELECT t.userId, COUNT(w) FROM WoTechnician t, WorkOrder w WHERE w.id = t.workOrderId AND t.orgId = :orgId"
            + " AND w.status IN ('SCHEDULED', 'IN_PROGRESS', 'ON_HOLD') GROUP BY t.userId")
    List<Object[]> openPerTechnician(UUID orgId);
}
