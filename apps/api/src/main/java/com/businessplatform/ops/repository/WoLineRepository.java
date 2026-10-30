package com.businessplatform.ops.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.ops.domain.WoLine;

public interface WoLineRepository extends JpaRepository<WoLine, UUID> {

    List<WoLine> findByWorkOrderIdOrderByPosition(UUID workOrderId);

    @Modifying
    @Query("DELETE FROM WoLine l WHERE l.workOrderId = :workOrderId")
    void deleteByWorkOrderId(UUID workOrderId);

    /** [userId, labour hours] on work orders completed in a period, per technician (each technician gets the full hours). */
    @Query(value = "SELECT t.user_id, COALESCE(SUM(l.quantity), 0) FROM svc_wo_line l JOIN svc_work_order w ON w.id = l.work_order_id"
            + " JOIN svc_wo_technician t ON t.work_order_id = w.id WHERE w.org_id = :orgId AND w.status = 'COMPLETED'"
            + " AND l.kind = 'LABOUR' AND w.completed_at >= :from AND w.completed_at < :to GROUP BY t.user_id", nativeQuery = true)
    List<Object[]> labourHours(UUID orgId, java.time.Instant from, java.time.Instant to);
}
