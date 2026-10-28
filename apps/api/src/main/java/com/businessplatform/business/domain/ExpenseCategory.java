package com.businessplatform.business.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A kind of expense (admin-managed); switched off rather than deleted so old expenses keep it. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "biz_expense_category")
public class ExpenseCategory {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_km", nullable = false)
    private String nameKm;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
