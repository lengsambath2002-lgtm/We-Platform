package com.businessplatform.education.domain;

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

/** A fee of an academic year for one grade level (or every grade when {@code gradeId} is null). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_fee_item")
public class FeeItem {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "year_id", nullable = false)
    private UUID yearId;

    @Column(name = "grade_id")
    private UUID gradeId;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_km")
    private String nameKm;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
