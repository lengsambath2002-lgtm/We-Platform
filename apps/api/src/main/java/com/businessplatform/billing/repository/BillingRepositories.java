package com.businessplatform.billing.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.billing.domain.PlatformInvoice;
import com.businessplatform.billing.domain.Plan;
import com.businessplatform.billing.domain.Subscription;

/** Plans and subscriptions. */
public final class BillingRepositories {

    private BillingRepositories() {
    }

    public interface Plans extends JpaRepository<Plan, String> {

        List<Plan> findAllByOrderBySortOrder();
    }

    public interface Subscriptions extends JpaRepository<Subscription, UUID> {

        List<Subscription> findByStatus(String status);

        /** Bytes the organisation's stored files take. */
        @Query(value = "SELECT COALESCE(SUM(size_bytes), 0) FROM stored_file WHERE org_id = :orgId", nativeQuery = true)
        long storageUsed(UUID orgId);
    }

    public interface Invoices extends JpaRepository<PlatformInvoice, UUID> {

        List<PlatformInvoice> findByOrgIdOrderByIssuedAtDesc(UUID orgId);

        List<PlatformInvoice> findByOrgIdAndStatus(UUID orgId, String status);

        List<PlatformInvoice> findByStatusOrderByDueAt(String status);

        List<PlatformInvoice> findTop200ByOrderByIssuedAtDesc();

        java.util.Optional<PlatformInvoice> findByIdAndOrgId(UUID id, UUID orgId);

        @Query(value = "SELECT nextval('billing_invoice_seq')", nativeQuery = true)
        long nextNumber();
    }
}
