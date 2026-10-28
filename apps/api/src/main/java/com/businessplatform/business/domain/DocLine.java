package com.businessplatform.business.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A line of a quote or an invoice; {@code discount} and {@code net} are computed by {@code Totals}. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "biz_line")
public class DocLine {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "quote_id")
    private UUID quoteId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(nullable = false)
    private short position;

    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private String description;

    private String unit;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "discount_pct", nullable = false)
    private BigDecimal discountPct;

    @Column(nullable = false)
    private boolean vat;

    @Column(nullable = false)
    private BigDecimal discount;

    @Column(nullable = false)
    private BigDecimal net;
}
