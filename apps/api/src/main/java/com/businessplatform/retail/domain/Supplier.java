package com.businessplatform.retail.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A company the organisation buys from; {@code paymentTermsDays}: how long after ordering a bill is due. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "retail_supplier")
public class Supplier {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_name")
    private String contactName;

    private String email;

    private String phone;

    private String address;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "payment_terms_days", nullable = false)
    private short paymentTermsDays;

    private String notes;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
