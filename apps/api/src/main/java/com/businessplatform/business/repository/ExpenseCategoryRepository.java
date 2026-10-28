package com.businessplatform.business.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.business.domain.ExpenseCategory;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {

    List<ExpenseCategory> findByOrgIdOrderBySortOrderAscNameEnAsc(UUID orgId);

    Optional<ExpenseCategory> findByIdAndOrgId(UUID id, UUID orgId);

    boolean existsByOrgIdAndNameEnIgnoreCaseAndIdNot(UUID orgId, String nameEn, UUID id);

    long countByOrgId(UUID orgId);

    /** Adds a default category unless one with that name exists (safe when two first uses race). */
    @Modifying
    @Query(value = "INSERT INTO biz_expense_category (id, org_id, name_en, name_km, active, sort_order)"
            + " VALUES (gen_random_uuid(), :orgId, :nameEn, :nameKm, TRUE, :sortOrder) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void seed(UUID orgId, String nameEn, String nameKm, short sortOrder);
}
