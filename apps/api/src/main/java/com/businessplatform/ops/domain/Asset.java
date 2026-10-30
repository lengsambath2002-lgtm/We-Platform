package com.businessplatform.ops.domain;

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

/** Equipment the organisation services, at a customer (and maybe one of their sites). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "svc_asset")
public class Asset {

    public static final String ACTIVE = "ACTIVE";
    public static final String IN_REPAIR = "IN_REPAIR";
    public static final String RETIRED = "RETIRED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(nullable = false)
    private String name;

    private String serial;

    private String model;

    private String manufacturer;

    @Column(name = "installed_on")
    private LocalDate installedOn;

    @Column(name = "warranty_end")
    private LocalDate warrantyEnd;

    @Column(nullable = false)
    private String status;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
