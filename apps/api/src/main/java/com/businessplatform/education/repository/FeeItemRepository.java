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

public interface FeeItemRepository extends JpaRepository<FeeItem, UUID> {

    List<FeeItem> findByOrgIdAndYearIdOrderByDueOnAscNameEnAsc(UUID orgId, UUID yearId);

    List<FeeItem> findByOrgIdOrderByDueOnDesc(UUID orgId);

    Optional<FeeItem> findByIdAndOrgId(UUID id, UUID orgId);

    @Query("SELECT f FROM FeeItem f WHERE f.orgId = :orgId AND f.dueOn BETWEEN :from AND :to ORDER BY f.dueOn")
    List<FeeItem> dueBetween(UUID orgId, LocalDate from, LocalDate to);
}
