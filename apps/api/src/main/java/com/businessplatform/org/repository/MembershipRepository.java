package com.businessplatform.org.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByOrgIdAndUserId(UUID orgId, UUID userId);

    List<Membership> findByUserIdAndStatus(UUID userId, String status);

    List<Membership> findByOrgIdOrderByCreatedAt(UUID orgId);

    long countByOrgIdAndRoleAndStatus(UUID orgId, OrgRole role, String status);

    boolean existsByOrgIdAndUserIdAndStatus(UUID orgId, UUID userId, String status);
}
