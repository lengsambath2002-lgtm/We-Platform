package com.businessplatform.education.domain;

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
 * A bill to a student, numbered FEE-0001 when created. ISSUED ─payments─► PARTIALLY_PAID ─► PAID; an admin
 * can make one VOID while nothing is paid on it. ISSUED or PARTIALLY_PAID past {@code dueOn} is overdue (derived).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_invoice")
public class FeeInvoice {

    public static final String ISSUED = "ISSUED";
    public static final String PARTIALLY_PAID = "PARTIALLY_PAID";
    public static final String PAID = "PAID";
    public static final String VOID = "VOID";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String number;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "fee_item_id")
    private UUID feeItemId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal paid;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(nullable = false)
    private String status;

    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Payments can still be recorded. */
    public boolean open() {
        return ISSUED.equals(status) || PARTIALLY_PAID.equals(status);
    }

    public BigDecimal balance() {
        return VOID.equals(status) ? BigDecimal.ZERO.setScale(2) : amount.subtract(paid);
    }

    public boolean overdue(LocalDate today) {
        return open() && dueOn.isBefore(today);
    }
}
