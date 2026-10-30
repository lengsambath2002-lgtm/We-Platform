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

/** A student's attendance in a class on one day (separate from employee attendance in HR). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_attendance")
public class AttendanceMark {

    public static final String PRESENT = "PRESENT";
    public static final String ABSENT = "ABSENT";
    public static final String LATE = "LATE";
    public static final String EXCUSED = "EXCUSED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false)
    private LocalDate day;

    @Column(nullable = false)
    private String status;

    private String note;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
