package com.businessplatform.hr.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.hr.domain.LeaveType;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

    List<LeaveType> findByOrgIdOrderBySortOrderAscNameEnAsc(UUID orgId);

    Optional<LeaveType> findByIdAndOrgId(UUID id, UUID orgId);

    boolean existsByOrgIdAndNameEnIgnoreCaseAndIdNot(UUID orgId, String nameEn, UUID id);

    long countByOrgId(UUID orgId);

    /** Adds a default type unless one with that name exists (safe when two first uses race). */
    @Modifying
    @Query(value = "INSERT INTO leave_type (id, org_id, name_en, name_km, paid, allowance_days, half_days, active, sort_order)"
            + " VALUES (gen_random_uuid(), :orgId, :nameEn, :nameKm, :paid, :allowance, TRUE, TRUE, :sortOrder)"
            + " ON CONFLICT DO NOTHING", nativeQuery = true)
    void seed(UUID orgId, String nameEn, String nameKm, boolean paid, java.math.BigDecimal allowance, short sortOrder);
}
