package com.businessplatform.hr.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.hr.domain.EmployeeContract;
import com.businessplatform.hr.domain.EmployeeDocument;
import com.businessplatform.hr.domain.PayChange;

/** Contracts, employee documents and salary history. */
public final class RecordRepositories {

    private RecordRepositories() {
    }

    public interface Contracts extends JpaRepository<EmployeeContract, UUID> {

        List<EmployeeContract> findByOrgIdAndUserIdOrderByStartsOnDesc(UUID orgId, UUID userId);

        List<EmployeeContract> findByOrgId(UUID orgId);

        Optional<EmployeeContract> findByIdAndOrgId(UUID id, UUID orgId);
    }

    public interface Documents extends JpaRepository<EmployeeDocument, UUID> {

        List<EmployeeDocument> findByOrgIdAndUserIdOrderByCreatedAtDesc(UUID orgId, UUID userId);

        List<EmployeeDocument> findByOrgIdAndExpiresOnIsNotNull(UUID orgId);

        Optional<EmployeeDocument> findByIdAndOrgId(UUID id, UUID orgId);
    }

    public interface PayChanges extends JpaRepository<PayChange, UUID> {

        List<PayChange> findByOrgIdAndUserIdOrderByEffectiveOnDescCreatedAtDesc(UUID orgId, UUID userId);
    }
}
