package com.businessplatform.ops.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A member's role in Service &amp; Operations: DISPATCHER (runs requests, tickets and work orders) or TECHNICIAN
 * (does the work orders assigned to them). Owners and Admins need no row. An inactive row gives no access.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "svc_staff")
public class Staff {

    public static final String DISPATCHER = "DISPATCHER";
    public static final String TECHNICIAN = "TECHNICIAN";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String role;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> skills = new ArrayList<>();

    private String phone;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
