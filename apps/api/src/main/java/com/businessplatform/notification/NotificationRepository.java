package com.businessplatform.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdAndOrgIdOrderByCreatedAtDesc(UUID userId, UUID orgId, Pageable page);

    long countByUserIdAndOrgIdAndReadAtIsNull(UUID userId, UUID orgId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :at WHERE n.userId = :userId AND n.orgId = :orgId AND n.readAt IS NULL"
            + " AND (:id IS NULL OR n.id = :id)")
    int markRead(UUID userId, UUID orgId, UUID id, Instant at);
}
