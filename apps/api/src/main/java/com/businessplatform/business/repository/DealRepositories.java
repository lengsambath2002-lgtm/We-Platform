package com.businessplatform.business.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.business.domain.Deal;
import com.businessplatform.business.domain.DealActivity;

/** The sales pipeline's repositories. */
public final class DealRepositories {

    private DealRepositories() {
    }

    public interface DealRepository extends JpaRepository<Deal, UUID> {

        List<Deal> findByOrgIdOrderByUpdatedAtDesc(UUID orgId);

        Optional<Deal> findByIdAndOrgId(UUID id, UUID orgId);

        List<Deal> findByOrgIdAndCustomerIdOrderByUpdatedAtDesc(UUID orgId, UUID customerId);
    }

    public interface DealActivityRepository extends JpaRepository<DealActivity, UUID> {

        List<DealActivity> findByDealIdOrderByHappenedOnDescCreatedAtDesc(UUID dealId);
    }
}
