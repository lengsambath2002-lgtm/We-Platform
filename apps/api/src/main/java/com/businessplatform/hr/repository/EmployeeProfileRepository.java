package com.businessplatform.hr.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.hr.domain.EmployeeProfile;

import jakarta.persistence.LockModeType;

public interface EmployeeProfileRepository extends JpaRepository<EmployeeProfile, UUID> {

    List<EmployeeProfile> findByOrgId(UUID orgId);

    Optional<EmployeeProfile> findByOrgIdAndUserId(UUID orgId, UUID userId);

    /** Locks one person's profile, so their leave requests are checked against their balance one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM EmployeeProfile p WHERE p.orgId = :orgId AND p.userId = :userId")
    Optional<EmployeeProfile> lock(UUID orgId, UUID userId);

    /** Creates the profiles of active non-guest members who have none (safe when two requests race). */
    @Modifying
    @Query(value = "INSERT INTO employee_profile (id, org_id, membership_id, user_id)"
            + " SELECT gen_random_uuid(), m.org_id, m.id, m.user_id FROM membership m"
            + " WHERE m.org_id = :orgId AND m.status = 'ACTIVE' AND m.role <> 'GUEST'"
            + " ON CONFLICT DO NOTHING", nativeQuery = true)
    int createMissing(UUID orgId);
}
