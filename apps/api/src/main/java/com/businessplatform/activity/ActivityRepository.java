package com.businessplatform.activity;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ActivityRepository extends JpaRepository<Activity, UUID>, JpaSpecificationExecutor<Activity> {

    List<Activity> findByOrgIdAndRecordTypeAndRecordIdOrderByCreatedAtAsc(UUID orgId, String recordType, UUID recordId);

    List<Activity> findByOrgIdAndRecordIdInOrderByCreatedAtDesc(UUID orgId, List<UUID> recordIds, Pageable page);

    List<Activity> findByOrgIdOrderByCreatedAtDesc(UUID orgId, Pageable page);
}
