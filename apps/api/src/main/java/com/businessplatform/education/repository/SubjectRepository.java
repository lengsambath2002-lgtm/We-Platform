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

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    List<Subject> findByOrgIdOrderByNameEn(UUID orgId);

    Optional<Subject> findByIdAndOrgId(UUID id, UUID orgId);

    boolean existsByOrgIdAndCodeIgnoreCaseAndIdNot(UUID orgId, String code, UUID id);

    long countByOrgId(UUID orgId);

    /** Adds a default subject unless one with that code exists (safe when two first uses race). */
    @Modifying
    @Query(value = "INSERT INTO edu_subject (id, org_id, code, name_en, name_km, active)"
            + " VALUES (gen_random_uuid(), :orgId, :code, :nameEn, :nameKm, TRUE) ON CONFLICT DO NOTHING", nativeQuery = true)
    void seed(UUID orgId, String code, String nameEn, String nameKm);
}
