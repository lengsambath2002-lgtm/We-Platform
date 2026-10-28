package com.businessplatform.business.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.business.domain.Invoice;

import jakarta.persistence.LockModeType;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByIdAndOrgId(UUID id, UUID orgId);

    /** The invoice, locked until the transaction ends (payments, issuing and voiding one at a time). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id AND i.orgId = :orgId")
    Optional<Invoice> lock(UUID id, UUID orgId);

    List<Invoice> findTop50ByOrgIdAndCustomerIdOrderByCreatedAtDesc(UUID orgId, UUID customerId);

    List<Invoice> findByOrgIdOrderByUpdatedAtDesc(UUID orgId, Pageable page);

    /** [customerId, balance] of the customers' issued, unpaid invoices. */
    @Query("SELECT i.customerId, SUM(i.total - i.paid) FROM Invoice i WHERE i.orgId = :orgId"
            + " AND i.customerId IN :customerIds AND i.status IN ('ISSUED', 'PARTIALLY_PAID') GROUP BY i.customerId")
    List<Object[]> balances(UUID orgId, Collection<UUID> customerIds);

    /** [count, sum of balances] of issued, unpaid invoices. */
    @Query("SELECT COUNT(i), COALESCE(SUM(i.total - i.paid), 0) FROM Invoice i WHERE i.orgId = :orgId"
            + " AND i.status IN ('ISSUED', 'PARTIALLY_PAID')")
    List<Object[]> receivable(UUID orgId);

    /** [count, sum of balances] of issued, unpaid invoices due before {@code before}. */
    @Query("SELECT COUNT(i), COALESCE(SUM(i.total - i.paid), 0) FROM Invoice i WHERE i.orgId = :orgId"
            + " AND i.status IN ('ISSUED', 'PARTIALLY_PAID') AND i.dueDate < :before")
    List<Object[]> overdue(UUID orgId, LocalDate before);

    /** Sum of the balances of one customer's issued, unpaid invoices due before {@code before}. */
    @Query("SELECT COALESCE(SUM(i.total - i.paid), 0) FROM Invoice i WHERE i.orgId = :orgId AND i.customerId = :customerId"
            + " AND i.status IN ('ISSUED', 'PARTIALLY_PAID') AND i.dueDate < :before")
    java.math.BigDecimal customerOverdue(UUID orgId, UUID customerId, LocalDate before);

    /** [invoiced, received] for one customer's issued invoices (void ones excluded). */
    @Query("SELECT COALESCE(SUM(i.total), 0), COALESCE(SUM(i.paid), 0) FROM Invoice i WHERE i.orgId = :orgId"
            + " AND i.customerId = :customerId AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')")
    List<Object[]> customerTotals(UUID orgId, UUID customerId);

    /** Issued, unpaid invoices due in {@code from}–{@code to}, soonest first. */
    @Query("SELECT i FROM Invoice i WHERE i.orgId = :orgId AND i.status IN ('ISSUED', 'PARTIALLY_PAID')"
            + " AND i.dueDate BETWEEN :from AND :to ORDER BY i.dueDate, i.number")
    List<Invoice> openDueBetween(UUID orgId, LocalDate from, LocalDate to);

    /** Issued invoices (paid or not, void excluded) due in {@code from}–{@code to}, for the calendar. */
    @Query("SELECT i FROM Invoice i WHERE i.orgId = :orgId AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')"
            + " AND i.dueDate BETWEEN :from AND :to ORDER BY i.dueDate, i.number")
    List<Invoice> dueBetween(UUID orgId, LocalDate from, LocalDate to);

    /** [first day of month, sum of totals] of invoices issued in {@code from}–{@code to} (void excluded). */
    @Query(value = "SELECT CAST(date_trunc('month', issue_date) AS DATE), SUM(total) FROM biz_invoice"
            + " WHERE org_id = :orgId AND status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')"
            + " AND issue_date BETWEEN :from AND :to GROUP BY 1", nativeQuery = true)
    List<Object[]> salesPerMonth(UUID orgId, LocalDate from, LocalDate to);

    boolean existsByOrgIdAndCustomerId(UUID orgId, UUID customerId);
}
