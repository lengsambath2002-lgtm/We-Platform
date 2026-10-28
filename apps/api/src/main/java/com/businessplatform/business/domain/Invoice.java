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
 * A bill to a customer. DRAFT (no number, editable) ─issued─► ISSUED (numbered INV-0001, fixed) ─payments─►
 * PARTIALLY_PAID ─► PAID; an issued invoice without payments can be made VOID (to correct it, void and issue
 * a new one). ISSUED or PARTIALLY_PAID past {@code dueDate} is overdue (derived).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "biz_invoice")
public class Invoice implements Totals.Doc {

    public static final String DRAFT = "DRAFT";
    public static final String ISSUED = "ISSUED";
    public static final String PARTIALLY_PAID = "PARTIALLY_PAID";
    public static final String PAID = "PAID";
    public static final String VOID = "VOID";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    private String number;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String status;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "vat_enabled", nullable = false)
    private boolean vatEnabled;

    @Column(name = "vat_rate", nullable = false)
    private BigDecimal vatRate;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(name = "discount_total", nullable = false)
    private BigDecimal discountTotal;

    @Column(name = "vat_total", nullable = false)
    private BigDecimal vatTotal;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(nullable = false)
    private BigDecimal paid;

    private String notes;

    @Column(name = "quote_id")
    private UUID quoteId;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "issued_by")
    private UUID issuedBy;

    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by")
    private UUID voidedBy;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Issued and not fully paid (money is owed). */
    public boolean open() {
        return ISSUED.equals(status) || PARTIALLY_PAID.equals(status);
    }

    public boolean overdue(LocalDate today) {
        return open() && dueDate != null && dueDate.isBefore(today);
    }

    public BigDecimal balance() {
        return open() ? total.subtract(paid) : BigDecimal.ZERO.setScale(2);
    }
}
