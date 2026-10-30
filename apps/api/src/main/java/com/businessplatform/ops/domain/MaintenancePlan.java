package com.businessplatform.ops.domain;

import java.time.Instant;
import java.time.LocalDate;
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
 * Preventive maintenance of an asset every {@code intervalCount} DAYS or MONTHS. Its next work order is generated
 * {@code leadDays} before {@code nextDue}; then {@code nextDue} moves on by one interval.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "svc_maintenance_plan")
public class MaintenancePlan {

    public static final String DAYS = "DAYS";
    public static final String MONTHS = "MONTHS";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(nullable = false)
    private String title;

    @Column(name = "interval_count", nullable = false)
    private short intervalCount;

    @Column(name = "interval_unit", nullable = false)
    private String intervalUnit;

    @Column(name = "next_due", nullable = false)
    private LocalDate nextDue;

    @Column(name = "lead_days", nullable = false)
    private short leadDays;

    @Column(name = "technician_id")
    private UUID technicianId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> checklist = new ArrayList<>();

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_generated_at")
    private Instant lastGeneratedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** The due date one interval after {@code from}. */
    public LocalDate after(LocalDate from) {
        return MONTHS.equals(intervalUnit) ? from.plusMonths(intervalCount) : from.plusDays(intervalCount);
    }

    /** The day its next work order is generated. */
    public LocalDate generateOn() {
        return nextDue.minusDays(leadDays);
    }
}
