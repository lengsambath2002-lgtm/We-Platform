package com.businessplatform.ops.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.businessplatform.ops.domain.OpsCustomer;

public interface OpsCustomerRepository extends JpaRepository<OpsCustomer, UUID>, JpaSpecificationExecutor<OpsCustomer> {

    Optional<OpsCustomer> findByIdAndOrgId(UUID id, UUID orgId);
}
