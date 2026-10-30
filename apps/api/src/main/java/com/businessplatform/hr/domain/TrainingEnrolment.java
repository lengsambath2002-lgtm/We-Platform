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

/** Someone on a course: ENROLLED → COMPLETED (HR marks it), or CANCELLED. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "training_enrolment")
public class TrainingEnrolment {

    public static final String ENROLLED = "ENROLLED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String status;

    @Column(name = "enrolled_by", nullable = false)
    private UUID enrolledBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
