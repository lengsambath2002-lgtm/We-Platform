package com.businessplatform.billing.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An organisation's plan and where it stands (trial, paid, late, cancelled, complimentary). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subscription")
public class Subscription {

    public static final String TRIALING = "TRIALING";
    public static final String ACTIVE = "ACTIVE";
    public static final String PAST_DUE = "PAST_DUE";
    public static final String CANCELLED = "CANCELLED";
    public static final String COMPLIMENTARY = "COMPLIMENTARY";

    @Id
    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "plan_code", nullable = false, length = 20)
    private String planCode;

    @Column(nullable = false, length = 14)
    private String status;

    @Column(name = "billing_cycle", nullable = false, length = 8)
    private String billingCycle = "MONTHLY";

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(length = 500)
    private String note;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
