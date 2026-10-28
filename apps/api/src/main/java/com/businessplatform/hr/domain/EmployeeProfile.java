package com.businessplatform.hr.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A person's HR record in one organisation (one per membership, created on first use). The job title is
 * the membership's title, so it reads the same everywhere. Employment details and the emergency contact
 * are private: the person and HR managers only.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "employee_profile")
public class EmployeeProfile {

    public static final List<String> EMPLOYMENT_TYPES = List.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERN");

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private String department;

    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "employment_type")
    private String employmentType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "work_phone")
    private String workPhone;

    @Column(name = "emergency_name")
    private String emergencyName;

    @Column(name = "emergency_phone")
    private String emergencyPhone;

    @Column(name = "emergency_relation")
    private String emergencyRelation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
