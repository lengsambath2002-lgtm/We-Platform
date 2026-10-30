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

/** A course or session; {@code capacity} null = no limit; {@code selfEnrol}: people sign themselves up. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "training_course")
public class TrainingCourse {

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String title;

    private String description;

    private String provider;

    private String location;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    private BigDecimal hours;

    private Short capacity;

    @Column(name = "self_enrol", nullable = false)
    private boolean selfEnrol;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
