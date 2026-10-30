package com.businessplatform.org.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A member's place in the structure: a unit, a position or both; one per member is the primary one. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "org_assignment")
public class OrgAssignment {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
