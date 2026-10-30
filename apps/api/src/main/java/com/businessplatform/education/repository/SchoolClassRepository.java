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

public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {

    List<SchoolClass> findByOrgIdAndYearIdOrderByName(UUID orgId, UUID yearId);

    List<SchoolClass> findByOrgId(UUID orgId);

    Optional<SchoolClass> findByIdAndOrgId(UUID id, UUID orgId);

    List<SchoolClass> findByIdInAndOrgId(Collection<UUID> ids, UUID orgId);

    List<SchoolClass> findByHomeroomTeacherId(UUID teacherId);

    boolean existsByYearIdAndNameIgnoreCaseAndIdNot(UUID yearId, String name, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM SchoolClass c WHERE c.id = :id AND c.orgId = :orgId")
    Optional<SchoolClass> lock(UUID id, UUID orgId);
}
