package com.businessplatform.business.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.business.domain.Quote;

import jakarta.persistence.LockModeType;

public interface QuoteRepository extends JpaRepository<Quote, UUID>, JpaSpecificationExecutor<Quote> {

    Optional<Quote> findByIdAndOrgId(UUID id, UUID orgId);

    /** The quote, locked until the transaction ends (one change at a time, e.g. a double "Convert"). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Quote q WHERE q.id = :id AND q.orgId = :orgId")
    Optional<Quote> lock(UUID id, UUID orgId);

    List<Quote> findTop50ByOrgIdAndCustomerIdOrderByCreatedAtDesc(UUID orgId, UUID customerId);
}
