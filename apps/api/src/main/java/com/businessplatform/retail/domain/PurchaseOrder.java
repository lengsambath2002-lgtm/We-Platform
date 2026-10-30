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

/** An order to a supplier: DRAFT → ORDERED → PARTIAL → RECEIVED, or CANCELLED before anything arrives. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "retail_po")
public class PurchaseOrder {

    public static final String DRAFT = "DRAFT";
    public static final String ORDERED = "ORDERED";
    public static final String PARTIAL = "PARTIAL";
    public static final String RECEIVED = "RECEIVED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    private String number;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(nullable = false)
    private String status;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "shop_business_id")
    private UUID shopBusinessId;

    private String notes;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(nullable = false)
    private BigDecimal paid;

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Sent to the supplier and not cancelled: it counts as money owed. */
    public boolean committed() {
        return ORDERED.equals(status) || PARTIAL.equals(status) || RECEIVED.equals(status);
    }
}
