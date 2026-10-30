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

/** A custom role held by a member, for the whole organisation ({@code unitId} null) or one unit and below. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "org_role_grant")
public class OrgRoleGrant {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
