package com.businessplatform.education.domain;

import java.io.Serializable;
import java.time.LocalDate;
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

/** A student in a class (at most one class per academic year); {@code leftOn} once they leave. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_enrollment")
@IdClass(Enrollment.Key.class)
public class Enrollment {

    @Id
    @Column(name = "class_id")
    private UUID classId;

    @Id
    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "year_id", nullable = false)
    private UUID yearId;

    @Column(name = "enrolled_on", nullable = false)
    private LocalDate enrolledOn;

    @Column(name = "left_on")
    private LocalDate leftOn;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID classId;
        private UUID studentId;
    }
}
