package com.businessplatform.hr.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.hr.domain.Holiday;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    List<Holiday> findByOrgIdAndDateBetweenOrderByDate(UUID orgId, LocalDate from, LocalDate to);

    Optional<Holiday> findByIdAndOrgId(UUID id, UUID orgId);

    boolean existsByOrgIdAndDate(UUID orgId, LocalDate date);
}
