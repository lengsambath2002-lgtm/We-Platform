package com.businessplatform.identity.repository;

import java.time.Instant;
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
}
