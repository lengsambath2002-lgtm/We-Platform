package com.businessplatform.hr.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One month's payroll: DRAFT (recalculated freely) → APPROVED (locked) → PAID (people see their payslips).
 * {@code exchangeRate}: riel per US dollar for the month's tax.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pay_run")
public class PayRun {

    public static final String DRAFT = "DRAFT";
    public static final String APPROVED = "APPROVED";
    public static final String PAID = "PAID";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    /** yyyy-MM. */
    @Column(nullable = false)
    private String month;

    @Column(name = "exchange_rate", nullable = false)
    private BigDecimal exchangeRate;

    @Column(nullable = false)
    private String status;

    private String note;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
