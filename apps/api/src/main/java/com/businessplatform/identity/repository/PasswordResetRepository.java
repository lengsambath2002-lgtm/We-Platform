package com.businessplatform.identity.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.identity.domain.PasswordReset;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, UUID> {

    Optional<PasswordReset> findByTokenHash(String hash);

    /** Retires every unused link of the user (a newer one, or the password was set). */
    @Modifying
    @Query("UPDATE PasswordReset r SET r.usedAt = :at WHERE r.userId = :userId AND r.usedAt IS NULL")
    int retireAll(UUID userId, Instant at);
}
