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
    @Query(value = "INSERT INTO employee_profile (id, org_id, membership_id, user_id, department)"
            + " SELECT gen_random_uuid(), m.org_id, m.id, m.user_id, org_department_of(m.id) FROM membership m"
            + " WHERE m.org_id = :orgId AND m.status = 'ACTIVE' AND m.role <> 'GUEST'"
            + " AND NOT EXISTS (SELECT 1 FROM employee_profile p WHERE p.membership_id = m.id)"
            + " ON CONFLICT DO NOTHING", nativeQuery = true)
    int createMissing(UUID orgId);

    /** Each profile's department as the organisation structure gives it (membership id, department or null). */
    @Query(value = "SELECT CAST(p.membership_id AS VARCHAR), org_department_of(p.membership_id) FROM employee_profile p"
            + " WHERE p.org_id = :orgId", nativeQuery = true)
    List<Object[]> structureDepartments(UUID orgId);
}
