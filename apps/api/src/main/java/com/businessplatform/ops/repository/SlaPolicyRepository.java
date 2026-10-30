package com.businessplatform.ops.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.ops.domain.SlaPolicy;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, SlaPolicy.Key> {

    List<SlaPolicy> findByOrgId(UUID orgId);
}
