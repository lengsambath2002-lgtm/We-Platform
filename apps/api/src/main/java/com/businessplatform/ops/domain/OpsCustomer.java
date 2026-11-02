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

/** Someone the organisation does service work for: the same customer record General Business sells to (the shared `customer` table; Service leaves the kind at its default and doesn't map the tax number). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customer")
public class OpsCustomer {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_name")
    private String contactName;

    private String phone;

    private String email;

    private String address;

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
