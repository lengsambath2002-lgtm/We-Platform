package com.businessplatform.org.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.org.domain.OrgAssignment;
import com.businessplatform.org.domain.OrgPosition;
import com.businessplatform.org.domain.OrgUnit;

/** The organisation structure's tables. */
public final class StructureRepositories {

    private StructureRepositories() {
    }

    public interface OrgUnitRepository extends JpaRepository<OrgUnit, UUID> {

        List<OrgUnit> findByOrgId(UUID orgId);

        Optional<OrgUnit> findByIdAndOrgId(UUID id, UUID orgId);

        @Query("SELECT count(u) > 0 FROM OrgUnit u WHERE u.orgId = :orgId AND lower(u.code) = lower(:code) AND u.id <> :id")
        boolean codeTaken(UUID orgId, String code, UUID id);

        @Query("""
                SELECT count(u) > 0 FROM OrgUnit u WHERE u.orgId = :orgId AND u.archivedAt IS NULL AND u.id <> :id
                AND lower(u.name) = lower(:name) AND ((:parentId IS NULL AND u.parentId IS NULL) OR u.parentId = :parentId)""")
        boolean nameTaken(UUID orgId, UUID parentId, String name, UUID id);
    }

    public interface OrgPositionRepository extends JpaRepository<OrgPosition, UUID> {

        List<OrgPosition> findByOrgId(UUID orgId);

        Optional<OrgPosition> findByIdAndOrgId(UUID id, UUID orgId);

        @Query("SELECT count(p) > 0 FROM OrgPosition p WHERE p.orgId = :orgId AND lower(p.code) = lower(:code) AND p.id <> :id")
        boolean codeTaken(UUID orgId, String code, UUID id);

        @Query("""
                SELECT count(p) > 0 FROM OrgPosition p WHERE p.orgId = :orgId AND p.archivedAt IS NULL AND p.id <> :id
                AND lower(p.title) = lower(:title) AND ((:unitId IS NULL AND p.unitId IS NULL) OR p.unitId = :unitId)""")
        boolean titleTaken(UUID orgId, UUID unitId, String title, UUID id);
    }

    public interface OrgAssignmentRepository extends JpaRepository<OrgAssignment, UUID> {

        List<OrgAssignment> findByOrgId(UUID orgId);

        List<OrgAssignment> findByMembershipIdOrderByCreatedAt(UUID membershipId);

        List<OrgAssignment> findByMembershipIdIn(Collection<UUID> membershipIds);

        List<OrgAssignment> findByPositionId(UUID positionId);

        boolean existsByUnitId(UUID unitId);

        boolean existsByPositionId(UUID positionId);

        @Modifying
        @Query("DELETE FROM OrgAssignment a WHERE a.membershipId = :membershipId")
        void deleteByMembershipId(UUID membershipId);
    }
}
