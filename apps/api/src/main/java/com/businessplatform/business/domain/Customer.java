package com.businessplatform.business.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A company or a person the organisation sells to. Archived customers stay on old documents but can't be used on new ones. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "biz_customer")
public class Customer {

    public static final String COMPANY = "COMPANY";
    public static final String INDIVIDUAL = "INDIVIDUAL";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_name")
    private String contactName;

    private String email;

    private String phone;

    private String address;

    @Column(name = "tax_id")
    private String taxId;

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
