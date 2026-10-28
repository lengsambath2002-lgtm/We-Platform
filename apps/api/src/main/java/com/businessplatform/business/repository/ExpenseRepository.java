package com.businessplatform.business.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.business.domain.Expense;

import jakarta.persistence.LockModeType;

public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    Optional<Expense> findByIdAndOrgId(UUID id, UUID orgId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Expense e WHERE e.id = :id AND e.orgId = :orgId")
    Optional<Expense> lock(UUID id, UUID orgId);

    /** [first day of month, sum of amounts] of approved or paid expenses spent in {@code from}–{@code to}. */
    @Query(value = "SELECT CAST(date_trunc('month', spent_on) AS DATE), SUM(amount) FROM biz_expense"
            + " WHERE org_id = :orgId AND status IN ('APPROVED', 'PAID') AND spent_on BETWEEN :from AND :to"
            + " GROUP BY 1", nativeQuery = true)
    List<Object[]> approvedPerMonth(UUID orgId, LocalDate from, LocalDate to);
}
