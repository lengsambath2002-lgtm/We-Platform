package com.businessplatform.ops.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A place of a customer where work happens (a branch, a building, a home). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "svc_site")
public class Site {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String name;

    private String address;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
