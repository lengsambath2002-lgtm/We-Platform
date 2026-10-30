package com.businessplatform.hr.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A position the organisation is hiring for; its hiring manager follows its candidates. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "job_opening")
public class JobOpening {

    public static final String OPEN = "OPEN";
    public static final String ON_HOLD = "ON_HOLD";
    public static final String CLOSED = "CLOSED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String title;

    private String department;

    @Column(name = "employment_type")
    private String employmentType;

    private String location;

    private String description;

    @Column(nullable = false)
    private short openings;

    @Column(name = "hiring_manager_id")
    private UUID hiringManagerId;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
