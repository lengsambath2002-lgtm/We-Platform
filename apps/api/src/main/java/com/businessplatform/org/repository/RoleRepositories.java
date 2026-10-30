package com.businessplatform.org.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.org.domain.OrgCustomRole;
import com.businessplatform.org.domain.OrgRoleGrant;

/** Custom roles and who holds them. */
public final class RoleRepositories {

    private RoleRepositories() {
    }

    public interface OrgCustomRoleRepository extends JpaRepository<OrgCustomRole, UUID> {

        List<OrgCustomRole> findByOrgIdOrderByName(UUID orgId);

        Optional<OrgCustomRole> findByIdAndOrgId(UUID id, UUID orgId);

        @Query("SELECT count(r) > 0 FROM OrgCustomRole r WHERE r.orgId = :orgId AND lower(r.name) = lower(:name) AND r.id <> :id")
        boolean nameTaken(UUID orgId, String name, UUID id);
    }

    public interface OrgRoleGrantRepository extends JpaRepository<OrgRoleGrant, UUID> {

        List<OrgRoleGrant> findByOrgId(UUID orgId);

        List<OrgRoleGrant> findByMembershipId(UUID membershipId);

        Optional<OrgRoleGrant> findByIdAndOrgId(UUID id, UUID orgId);

        boolean existsByUnitId(UUID unitId);

        @Query("""
                SELECT count(g) > 0 FROM OrgRoleGrant g WHERE g.roleId = :roleId AND g.membershipId = :membershipId
                AND ((:unitId IS NULL AND g.unitId IS NULL) OR g.unitId = :unitId)""")
        boolean exists(UUID roleId, UUID membershipId, UUID unitId);
    }
}
