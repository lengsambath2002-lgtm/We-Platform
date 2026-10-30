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

public interface GuardianRepository extends JpaRepository<Guardian, UUID>, JpaSpecificationExecutor<Guardian> {

    Optional<Guardian> findByIdAndOrgId(UUID id, UUID orgId);

    List<Guardian> findByIdInAndOrgId(Collection<UUID> ids, UUID orgId);
}
