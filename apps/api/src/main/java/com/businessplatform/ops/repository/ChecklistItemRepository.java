package com.businessplatform.ops.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.ops.domain.ChecklistItem;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, UUID> {

    List<ChecklistItem> findByWorkOrderIdOrderByPosition(UUID workOrderId);

    List<ChecklistItem> findByWorkOrderIdIn(Collection<UUID> workOrderIds);

    Optional<ChecklistItem> findByIdAndWorkOrderId(UUID id, UUID workOrderId);

    @Modifying
    @Query("DELETE FROM ChecklistItem c WHERE c.workOrderId = :workOrderId")
    void deleteByWorkOrderId(UUID workOrderId);
}
