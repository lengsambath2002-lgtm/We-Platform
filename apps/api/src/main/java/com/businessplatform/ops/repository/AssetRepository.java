package com.businessplatform.ops.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.ops.domain.Asset;

public interface AssetRepository extends JpaRepository<Asset, UUID>, JpaSpecificationExecutor<Asset> {

    Optional<Asset> findByIdAndOrgId(UUID id, UUID orgId);

    List<Asset> findByOrgIdAndCustomerIdOrderByName(UUID orgId, UUID customerId);

    @Query("SELECT COUNT(a) > 0 FROM Asset a WHERE a.orgId = :orgId AND lower(a.serial) = lower(:serial) AND a.id <> :id")
    boolean serialTaken(UUID orgId, String serial, UUID id);
}
