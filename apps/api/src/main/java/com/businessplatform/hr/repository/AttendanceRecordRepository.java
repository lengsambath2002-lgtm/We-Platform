package com.businessplatform.hr.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.hr.domain.AttendanceRecord;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    Optional<AttendanceRecord> findByOrgIdAndUserIdAndWorkDate(UUID orgId, UUID userId, LocalDate workDate);

    Optional<AttendanceRecord> findByIdAndOrgId(UUID id, UUID orgId);

    List<AttendanceRecord> findByOrgIdAndWorkDate(UUID orgId, LocalDate workDate);

    List<AttendanceRecord> findByOrgIdAndUserIdAndWorkDateBetweenOrderByWorkDate(UUID orgId, UUID userId, LocalDate from,
            LocalDate to);
}
