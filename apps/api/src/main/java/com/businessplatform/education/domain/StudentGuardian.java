package com.businessplatform.education.domain;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A guardian of a student, with the relationship; one of a student's guardians may be the primary contact. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_student_guardian")
@IdClass(StudentGuardian.Key.class)
public class StudentGuardian {

    @Id
    @Column(name = "student_id")
    private UUID studentId;

    @Id
    @Column(name = "guardian_id")
    private UUID guardianId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    /** MOTHER, FATHER, GUARDIAN, GRANDPARENT, SIBLING or OTHER. */
    @Column(nullable = false)
    private String relationship;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID studentId;
        private UUID guardianId;
    }
}
