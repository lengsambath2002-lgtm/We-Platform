package com.businessplatform.ops.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.ops.domain.Site;

public interface SiteRepository extends JpaRepository<Site, UUID> {

    Optional<Site> findByIdAndOrgId(UUID id, UUID orgId);

    List<Site> findByOrgIdAndCustomerIdOrderByName(UUID orgId, UUID customerId);

    List<Site> findByOrgIdAndCustomerIdIn(UUID orgId, Collection<UUID> customerIds);

    /** Whether a ticket, asset or work order points at the site. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM svc_ticket WHERE site_id = :id) OR EXISTS (SELECT 1 FROM svc_asset"
            + " WHERE site_id = :id) OR EXISTS (SELECT 1 FROM svc_work_order WHERE site_id = :id)", nativeQuery = true)
    boolean inUse(UUID id);
}
