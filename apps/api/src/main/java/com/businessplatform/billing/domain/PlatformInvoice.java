package com.businessplatform.billing.domain;

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

/** The platform's invoice to an organisation for one paid period. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "billing_invoice")
public class PlatformInvoice {

    public static final String OPEN = "OPEN";
    public static final String PAID = "PAID";
    public static final String VOID = "VOID";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false, length = 20)
    private String number;

    @Column(name = "plan_code", nullable = false, length = 20)
    private String planCode;

    @Column(name = "billing_cycle", nullable = false, length = 8)
    private String billingCycle;

    @Column(nullable = false)
    private int seats;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(nullable = false, length = 6)
    private String status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(length = 400)
    private String khqr;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claim_reference", length = 120)
    private String claimReference;

    @Column(name = "claimed_by")
    private UUID claimedBy;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payment_method", length = 14)
    private String paymentMethod;

    @Column(name = "payment_reference", length = 120)
    private String paymentReference;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "void_reason", length = 300)
    private String voidReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
