package com.businessplatform.hr.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A kind of leave the organisation offers; {@code allowanceDays} null = unlimited (no balance kept). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "leave_type")
public class LeaveType {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_km", nullable = false)
    private String nameKm;

    @Column(nullable = false)
    private boolean paid;

    @Column(name = "allowance_days")
    private BigDecimal allowanceDays;

    @Column(name = "half_days", nullable = false)
    private boolean halfDays;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean limited() {
        return allowanceDays != null;
    }
}
