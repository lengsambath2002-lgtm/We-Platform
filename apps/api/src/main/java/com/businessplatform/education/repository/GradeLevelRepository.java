package com.businessplatform.education.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.education.domain.*;

import jakarta.persistence.LockModeType;

public interface GradeLevelRepository extends JpaRepository<GradeLevel, UUID> {

    List<GradeLevel> findByOrgIdOrderByPositionAscNameEnAsc(UUID orgId);

    Optional<GradeLevel> findByIdAndOrgId(UUID id, UUID orgId);

    boolean existsByOrgIdAndNameEnIgnoreCaseAndIdNot(UUID orgId, String nameEn, UUID id);

    long countByOrgId(UUID orgId);

    /** Adds a default grade unless one with that name exists (safe when two first uses race). */
    @Modifying
    @Query(value = "INSERT INTO edu_grade (id, org_id, name_en, name_km, position, active)"
            + " VALUES (gen_random_uuid(), :orgId, :nameEn, :nameKm, :position, TRUE) ON CONFLICT DO NOTHING", nativeQuery = true)
    void seed(UUID orgId, String nameEn, String nameKm, short position);
}
