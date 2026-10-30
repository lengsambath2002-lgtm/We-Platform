package com.businessplatform.education.domain;

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

/**
 * A student, numbered STU-0001 at registration. ACTIVE ─► WITHDRAWN (an approved withdrawal request) or
 * GRADUATED (an admin); {@code leftOn} and {@code leaveReason} say when and why.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_student")
public class Student {

    public static final String ACTIVE = "ACTIVE";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String GRADUATED = "GRADUATED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String number;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_km")
    private String nameKm;

    @Column(nullable = false)
    private String gender;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "admitted_on", nullable = false)
    private LocalDate admittedOn;

    private String address;

    @Column(name = "medical_notes")
    private String medicalNotes;

    @Column(nullable = false)
    private String status;

    @Column(name = "left_on")
    private LocalDate leftOn;

    @Column(name = "leave_reason")
    private String leaveReason;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean active() {
        return ACTIVE.equals(status);
    }
}
