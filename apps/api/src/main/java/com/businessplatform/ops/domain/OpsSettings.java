package com.businessplatform.ops.domain;

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

/** An organisation's Service &amp; Operations settings (one row, created on first use). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "svc_settings")
public class OpsSettings {

    @Id
    @Column(name = "org_id")
    private UUID orgId;

    /** Work orders whose total is above this need approval before they are scheduled; null = never. */
    @Column(name = "approval_threshold")
    private BigDecimal approvalThreshold;

    /** The starting unit price of a labour hour on new lines. */
    @Column(name = "labour_rate", nullable = false)
    private BigDecimal labourRate;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
