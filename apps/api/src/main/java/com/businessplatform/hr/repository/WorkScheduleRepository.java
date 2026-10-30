package com.businessplatform.hr.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.hr.domain.WorkSchedule;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, UUID> {

    List<WorkSchedule> findByOrgIdOrderByName(UUID orgId);

    Optional<WorkSchedule> findByIdAndOrgId(UUID id, UUID orgId);

    Optional<WorkSchedule> findByOrgIdAndDefaultScheduleTrue(UUID orgId);

    boolean existsByOrgIdAndNameIgnoreCase(UUID orgId, String name);

    long countByOrgId(UUID orgId);
}
