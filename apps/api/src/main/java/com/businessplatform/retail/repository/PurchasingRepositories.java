package com.businessplatform.retail.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.retail.domain.PoLine;
import com.businessplatform.retail.domain.PoReceipt;
import com.businessplatform.retail.domain.PurchaseOrder;
import com.businessplatform.retail.domain.Supplier;
import com.businessplatform.retail.domain.SupplierPayment;

import jakarta.persistence.LockModeType;

/** Purchasing's repositories. */
public final class PurchasingRepositories {

    private PurchasingRepositories() {
    }

    public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

        List<Supplier> findByOrgIdOrderByNameAsc(UUID orgId);

        Optional<Supplier> findByIdAndOrgId(UUID id, UUID orgId);
    }

    public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

        List<PurchaseOrder> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

        List<PurchaseOrder> findByOrgIdAndSupplierIdOrderByCreatedAtDesc(UUID orgId, UUID supplierId);

        Optional<PurchaseOrder> findByIdAndOrgId(UUID id, UUID orgId);

        /** One change at a time to an order (receiving and paying update its totals). */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM PurchaseOrder p WHERE p.id = :id AND p.orgId = :orgId")
        Optional<PurchaseOrder> lock(UUID id, UUID orgId);

        /** The next PO number for the organisation (1, 2, …): one atomic upsert, safe when two orders are placed together. */
        @Query(value = "INSERT INTO retail_sequence (org_id, last_value) VALUES (:orgId, 1)"
                + " ON CONFLICT (org_id) DO UPDATE SET last_value = retail_sequence.last_value + 1"
                + " RETURNING last_value", nativeQuery = true)
        int nextNumber(UUID orgId);
    }

    public interface PoLineRepository extends JpaRepository<PoLine, UUID> {

        List<PoLine> findByPoIdOrderBySortOrder(UUID poId);

        void deleteByPoId(UUID poId);
    }

    public interface PoReceiptRepository extends JpaRepository<PoReceipt, UUID> {

        List<PoReceipt> findByPoIdOrderByReceivedOnDescCreatedAtDesc(UUID poId);

        Optional<PoReceipt> findByIdAndOrgId(UUID id, UUID orgId);
    }

    public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, UUID> {

        List<SupplierPayment> findByPoIdOrderByPaidOnDescCreatedAtDesc(UUID poId);
    }
}
