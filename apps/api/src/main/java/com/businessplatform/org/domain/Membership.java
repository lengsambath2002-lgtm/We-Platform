package com.businessplatform.org.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A person in an organisation, with their role; one row per (organisation, person). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "membership")
public class Membership {

    public static final String ACTIVE = "ACTIVE";
    public static final String SUSPENDED = "SUSPENDED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrgRole role;

    @Column(nullable = false, length = 10)
    private String status = ACTIVE;

    @Column(length = 80)
    private String title;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
