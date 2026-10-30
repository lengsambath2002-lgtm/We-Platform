package com.businessplatform.retail.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One product on a purchase order; {@code sku}: its code in VirtualBiz. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "retail_po_line")
public class PoLine {

    @Id
    private UUID id;

    @Column(name = "po_id", nullable = false)
    private UUID poId;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(nullable = false)
    private String description;

    private String sku;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @Column(nullable = false)
    private BigDecimal received;
}
