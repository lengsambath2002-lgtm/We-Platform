package com.businessplatform.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.identity.domain.TwoStepChallenge;
import com.businessplatform.identity.domain.TwoStepRecoveryCode;

/** Recovery codes and sign-in challenges of two-step sign-in. */
public final class TwoStepRepositories {

    private TwoStepRepositories() {
    }

    public interface RecoveryCodes extends JpaRepository<TwoStepRecoveryCode, UUID> {

        List<TwoStepRecoveryCode> findByUserIdAndUsedAtIsNull(UUID userId);

        @Modifying
        @Query("DELETE FROM TwoStepRecoveryCode c WHERE c.userId = :userId")
        int deleteAllOf(UUID userId);
    }

    public interface Challenges extends JpaRepository<TwoStepChallenge, UUID> {

        Optional<TwoStepChallenge> findByTokenHash(String hash);

        @Modifying
        @Query("UPDATE TwoStepChallenge c SET c.usedAt = :at WHERE c.userId = :userId AND c.usedAt IS NULL")
        int retireAll(UUID userId, Instant at);
    }
}
