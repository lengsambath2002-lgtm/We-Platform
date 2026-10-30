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

public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {

    Optional<Withdrawal> findByIdAndOrgId(UUID id, UUID orgId);

    List<Withdrawal> findByOrgIdAndStudentIdOrderByCreatedAtDesc(UUID orgId, UUID studentId);

    List<Withdrawal> findByOrgIdAndStatusOrderByCreatedAtAsc(UUID orgId, String status);

    boolean existsByStudentIdAndStatus(UUID studentId, String status);
}
