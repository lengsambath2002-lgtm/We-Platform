package com.businessplatform.education.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.education.domain.*;

import jakarta.persistence.LockModeType;

public interface FeeInvoiceRepository extends JpaRepository<FeeInvoice, UUID>, JpaSpecificationExecutor<FeeInvoice> {

    Optional<FeeInvoice> findByIdAndOrgId(UUID id, UUID orgId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM FeeInvoice i WHERE i.id = :id AND i.orgId = :orgId")
    Optional<FeeInvoice> lock(UUID id, UUID orgId);

    List<FeeInvoice> findByOrgIdAndStudentIdOrderByIssuedOnDescNumberDesc(UUID orgId, UUID studentId);

    List<FeeInvoice> findByFeeItemIdIn(Collection<UUID> feeItemIds);

    @Query("SELECT i.studentId FROM FeeInvoice i WHERE i.feeItemId = :feeItemId AND i.status <> 'VOID'")
    List<UUID> billedStudents(UUID feeItemId);

    /** [studentId, balance] of the students' unpaid invoices. */
    @Query("SELECT i.studentId, SUM(i.amount - i.paid) FROM FeeInvoice i WHERE i.orgId = :orgId"
            + " AND i.studentId IN :studentIds AND i.status IN ('ISSUED', 'PARTIALLY_PAID') GROUP BY i.studentId")
    List<Object[]> balances(UUID orgId, Collection<UUID> studentIds);

    /** [count, sum of balances] of unpaid invoices due before {@code before} (all unpaid when null). */
    @Query("SELECT COUNT(i), COALESCE(SUM(i.amount - i.paid), 0) FROM FeeInvoice i WHERE i.orgId = :orgId"
            + " AND i.status IN ('ISSUED', 'PARTIALLY_PAID') AND i.dueOn < :before")
    List<Object[]> overdue(UUID orgId, LocalDate before);

    @Query("SELECT COUNT(i), COALESCE(SUM(i.amount - i.paid), 0) FROM FeeInvoice i WHERE i.orgId = :orgId"
            + " AND i.status IN ('ISSUED', 'PARTIALLY_PAID')")
    List<Object[]> outstanding(UUID orgId);

    /** Invoices that are not void, of the organisation (for the fees report). */
    @Query("SELECT i FROM FeeInvoice i WHERE i.orgId = :orgId AND i.status <> 'VOID'")
    List<FeeInvoice> billed(UUID orgId);
}
