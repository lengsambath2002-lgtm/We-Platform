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

/** Money received against a fee invoice. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_payment")
public class FeePayment {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(nullable = false)
    private BigDecimal amount;

    /** CASH, BANK_TRANSFER, KHQR, CARD or OTHER. */
    @Column(nullable = false)
    private String method;

    private String reference;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
