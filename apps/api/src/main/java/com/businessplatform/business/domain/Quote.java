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
 * A price offer to a customer, numbered (Q-0001) when created: DRAFT ─sent─► SENT ─► ACCEPTED / DECLINED.
 * A SENT quote past {@code validUntil} is expired (derived). An accepted quote converts once into a draft invoice.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "biz_quote")
public class Quote implements Totals.Doc {

    public static final String DRAFT = "DRAFT";
    public static final String SENT = "SENT";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String number;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String status;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "valid_until")
    private LocalDate validUntil;

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

    private String notes;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean expired(LocalDate today) {
        return SENT.equals(status) && validUntil != null && validUntil.isBefore(today);
    }
}
