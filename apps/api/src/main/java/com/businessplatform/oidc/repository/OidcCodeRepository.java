package com.businessplatform.oidc.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.oidc.domain.OidcCode;

public interface OidcCodeRepository extends JpaRepository<OidcCode, UUID> {

    Optional<OidcCode> findByCodeHash(String hash);

    /** Marks the code used; 0 when it already was (a concurrent or repeated exchange). */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OidcCode c SET c.usedAt = :at WHERE c.id = :id AND c.usedAt IS NULL")
    int consume(UUID id, Instant at);

    /** Codes past their life (kept a day for the record, then removed). */
    @Modifying
    @Query("DELETE FROM OidcCode c WHERE c.expiresAt < :before")
    int purge(Instant before);
}
