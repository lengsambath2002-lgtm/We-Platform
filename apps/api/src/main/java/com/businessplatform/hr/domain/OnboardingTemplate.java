package com.businessplatform.hr.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One of the organisation's standard onboarding steps; due {@code dueDays} after the start date. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "onboarding_template")
public class OnboardingTemplate {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String title;

    @Column(name = "due_days", nullable = false)
    private short dueDays;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;
}
