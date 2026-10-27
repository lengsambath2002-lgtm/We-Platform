package com.businessplatform.org.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.org.domain.Invitation;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String hash);

    List<Invitation> findByOrgIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(UUID orgId);
}
