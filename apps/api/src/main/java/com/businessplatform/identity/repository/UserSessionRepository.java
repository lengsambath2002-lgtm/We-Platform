package com.businessplatform.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.identity.domain.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByRefreshTokenHash(String hash);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :at WHERE s.userId = :userId AND s.revokedAt IS NULL")
    int revokeAll(UUID userId, Instant at);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :at WHERE s.userId = :userId AND s.familyId = :familyId AND s.revokedAt IS NULL")
    int revokeFamily(UUID userId, UUID familyId, Instant at);

    /** Every session of the user except the device {@code keepFamilyId}. */
    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :at WHERE s.userId = :userId AND s.familyId <> :keepFamilyId AND s.revokedAt IS NULL")
    int revokeOthers(UUID userId, UUID keepFamilyId, Instant at);

    /** The live row of each signed-in device, newest use first. */
    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.expiresAt > :now ORDER BY s.lastUsedAt DESC")
    List<UserSession> findActive(UUID userId, Instant now);

    /** Whether the device that issued access token {@code sid} is still signed in (a refresh keeps it so). */
    @Query("""
            SELECT COUNT(s) > 0 FROM UserSession s WHERE s.revokedAt IS NULL AND s.expiresAt > :now
              AND s.familyId = (SELECT x.familyId FROM UserSession x WHERE x.id = :sid)""")
    boolean isSignedIn(UUID sid, Instant now);
}
