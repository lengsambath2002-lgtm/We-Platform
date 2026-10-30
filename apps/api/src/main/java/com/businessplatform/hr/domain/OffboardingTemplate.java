package com.businessplatform.hr.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A step every offboarding starts with; {@code dueDays} relative to the last day (negative = before). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "offboarding_template")
public class OffboardingTemplate {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "due_days", nullable = false)
    private short dueDays;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;
}
