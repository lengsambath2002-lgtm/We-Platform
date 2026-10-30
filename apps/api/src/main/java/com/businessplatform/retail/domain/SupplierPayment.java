package com.businessplatform.retail.domain;

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

/** Money paid to a supplier against a purchase order. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "retail_supplier_payment")
public class SupplierPayment {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "po_id", nullable = false)
    private UUID poId;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String method;

    private String reference;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
