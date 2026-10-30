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

public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID> {

    List<AcademicYear> findByOrgIdOrderByStartsOnDesc(UUID orgId);

    Optional<AcademicYear> findByIdAndOrgId(UUID id, UUID orgId);

    Optional<AcademicYear> findByOrgIdAndCurrentTrue(UUID orgId);

    boolean existsByOrgIdAndNameIgnoreCaseAndIdNot(UUID orgId, String name, UUID id);

    @Modifying
    @Query("UPDATE AcademicYear y SET y.current = false WHERE y.orgId = :orgId AND y.current = true")
    void clearCurrent(UUID orgId);
}
