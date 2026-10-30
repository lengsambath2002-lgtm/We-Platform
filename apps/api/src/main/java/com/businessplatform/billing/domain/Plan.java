package com.businessplatform.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A SaaS plan: price per member, member and storage limits, the modules it includes, its trial. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "plan")
public class Plan {

    public static final String FREE = "FREE";

    @Id
    private String code;

    @Column(name = "name_en", nullable = false, length = 60)
    private String nameEn;

    @Column(name = "name_km", nullable = false, length = 60)
    private String nameKm;

    /** Per member per month, in USD. */
    @Column(name = "price_monthly", nullable = false)
    private BigDecimal priceMonthly;

    /** Per member per year, in USD. */
    @Column(name = "price_yearly", nullable = false)
    private BigDecimal priceYearly;

    /** Members who aren't guests; null = no limit. */
    @Column(name = "max_members")
    private Integer maxMembers;

    @Column(name = "storage_gb", nullable = false)
    private int storageGb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> modules = new ArrayList<>();

    @Column(name = "trial_days", nullable = false)
    private short trialDays;

    @Column(name = "public", nullable = false)
    private boolean publicPlan;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
