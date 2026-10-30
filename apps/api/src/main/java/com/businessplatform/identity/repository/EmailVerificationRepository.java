package com.businessplatform.identity.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.identity.domain.EmailVerification;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {

    Optional<EmailVerification> findByTokenHash(String hash);

    /** Retires every unused link of the user (a newer one, or the email is confirmed). */
    @Modifying
    @Query("UPDATE EmailVerification v SET v.usedAt = :at WHERE v.userId = :userId AND v.usedAt IS NULL")
    int retireAll(UUID userId, Instant at);
}
