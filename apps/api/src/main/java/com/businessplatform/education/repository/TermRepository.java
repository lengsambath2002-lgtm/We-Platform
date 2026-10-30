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

public interface TermRepository extends JpaRepository<Term, UUID> {

    List<Term> findByYearIdOrderByStartsOn(UUID yearId);

    List<Term> findByOrgIdOrderByStartsOn(UUID orgId);

    Optional<Term> findByIdAndOrgId(UUID id, UUID orgId);

    boolean existsByYearIdAndNameIgnoreCaseAndIdNot(UUID yearId, String name, UUID id);
}
