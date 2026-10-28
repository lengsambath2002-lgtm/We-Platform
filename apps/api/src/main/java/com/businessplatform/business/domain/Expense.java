package com.businessplatform.business.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Money someone spent for the organisation. Decided through the approval engine ({@code approvalId}):
 * DRAFT ─submitted─► PENDING ─approved─► APPROVED ─reimbursed─► PAID; PENDING ─► REJECTED, or back to DRAFT
 * when the person withdraws it. {@code amount} includes {@code vatAmount}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "biz_expense")
public class Expense {

    public static final String DRAFT = "DRAFT";
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String PAID = "PAID";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    /** Who spent it (and submits it). */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    private String supplier;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "vat_amount")
    private BigDecimal vatAmount;

    private String notes;

    @Column(name = "receipt_file_id")
    private UUID receiptFileId;

    @Column(nullable = false)
    private String status;

    @Column(name = "approval_id")
    private UUID approvalId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
