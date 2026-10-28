package com.businessplatform.oidc.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.oidc.domain.OidcConsent;

public interface OidcConsentRepository extends JpaRepository<OidcConsent, UUID> {

    Optional<OidcConsent> findByUserIdAndClientId(UUID userId, String clientId);

    List<OidcConsent> findByUserIdOrderByGrantedAtDesc(UUID userId);

    @Modifying
    @Query("DELETE FROM OidcConsent c WHERE c.userId = :userId AND c.clientId = :clientId")
    int revoke(UUID userId, String clientId);
}
