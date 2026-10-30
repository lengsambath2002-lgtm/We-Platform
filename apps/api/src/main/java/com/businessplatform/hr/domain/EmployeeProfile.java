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

    /** The 2nd approver in the approval line; null = the manager's manager. */
    @Column(name = "second_manager_id")
    private UUID secondManagerId;

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

    /** The last working day of someone who left (offboarding completed). */
    @Column(name = "left_on")
    private java.time.LocalDate leftOn;

    // Personal details (private: the person and HR).

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "duty")
    private String duty;

    @Column(name = "category")
    private String category;

    @Column(name = "gender")
    private String gender;

    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    @Column(name = "nationality")
    private String nationality;

    @Column(name = "id_type")
    private String idType;

    @Column(name = "id_number")
    private String idNumber;

    @Column(name = "id_expires_on")
    private java.time.LocalDate idExpiresOn;

    @Column(name = "nssf_number")
    private String nssfNumber;

    @Column(name = "home_address")
    private String homeAddress;

    @Column(name = "personal_email")
    private String personalEmail;

    @Column(name = "personal_phone")
    private String personalPhone;

    /** The person's work schedule; null = the organisation's default. */
    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
