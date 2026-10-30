package com.businessplatform.ops.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.ops.domain.Staff;

public interface StaffRepository extends JpaRepository<Staff, UUID> {

    Optional<Staff> findByOrgIdAndUserId(UUID orgId, UUID userId);

    Optional<Staff> findByIdAndOrgId(UUID id, UUID orgId);

    List<Staff> findByOrgIdOrderByCreatedAt(UUID orgId);
}
