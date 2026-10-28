package com.businessplatform.business.domain;

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

/** Something the organisation sells (a product or a service), priced in USD. Switched off rather than deleted. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "biz_product")
public class Product {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_km")
    private String nameKm;

    @Column(nullable = false)
    private String unit;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    /** VAT applies to it by default. */
    @Column(nullable = false)
    private boolean vat;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
