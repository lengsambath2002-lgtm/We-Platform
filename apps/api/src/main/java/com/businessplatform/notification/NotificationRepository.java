package com.businessplatform.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdAndOrgIdOrderByCreatedAtDesc(UUID userId, UUID orgId, Pageable page);

    Page<Notification> findByUserIdAndOrgId(UUID userId, UUID orgId, Pageable page);

    Page<Notification> findByUserIdAndOrgIdAndReadAtIsNull(UUID userId, UUID orgId, Pageable page);

    long countByUserIdAndOrgIdAndReadAtIsNull(UUID userId, UUID orgId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :at WHERE n.userId = :userId AND n.orgId = :orgId AND n.readAt IS NULL"
            + " AND (:id IS NULL OR n.id = :id)")
    int markRead(UUID userId, UUID orgId, UUID id, Instant at);

    /** The kinds a member switched off. */
    @Query(value = "SELECT nm.kind FROM notification_mute nm WHERE nm.membership_id = :membershipId", nativeQuery = true)
    List<String> mutedKinds(UUID membershipId);

    /** Of {@code userIds}, those who switched {@code kind} off in the organisation. */
    @Query(value = "SELECT m.user_id FROM notification_mute nm JOIN membership m ON m.id = nm.membership_id"
            + " WHERE m.org_id = :orgId AND nm.kind = :kind AND m.user_id IN :userIds", nativeQuery = true)
    List<UUID> muting(UUID orgId, String kind, Collection<UUID> userIds);

    @Modifying
    @Query(value = "DELETE FROM notification_mute WHERE membership_id = :membershipId", nativeQuery = true)
    void clearMutes(UUID membershipId);

    @Modifying
    @Query(value = "INSERT INTO notification_mute (membership_id, kind) VALUES (:membershipId, :kind)", nativeQuery = true)
    void mute(UUID membershipId, String kind);
}
