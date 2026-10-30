package com.businessplatform.hr.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One change of someone's monthly salary (salary history). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pay_change")
public class PayChange {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "monthly_salary", nullable = false)
    private BigDecimal monthlySalary;

    private BigDecimal previous;

    @Column(name = "effective_on", nullable = false)
    private LocalDate effectiveOn;

    @Column(length = 300)
    private String reason;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
