package com.businessplatform.hr.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.hr.domain.OvertimeRequest;

public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, UUID> {

    Optional<OvertimeRequest> findByIdAndOrgId(UUID id, UUID orgId);

    long countByOrgIdAndStatus(UUID orgId, String status);

    List<OvertimeRequest> findByOrgIdAndUserIdOrderByWorkDateDesc(UUID orgId, UUID userId);

    List<OvertimeRequest> findByOrgIdAndStatusInOrderByWorkDateDesc(UUID orgId, Collection<String> statuses);

    List<OvertimeRequest> findByOrgIdOrderByWorkDateDesc(UUID orgId);

    List<OvertimeRequest> findByOrgIdAndUserIdAndWorkDateBetween(UUID orgId, UUID userId, LocalDate from, LocalDate to);

    boolean existsByOrgIdAndUserIdAndWorkDateAndStatusIn(UUID orgId, UUID userId, LocalDate workDate,
            Collection<String> statuses);
}
