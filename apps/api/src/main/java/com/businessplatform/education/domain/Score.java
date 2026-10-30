package com.businessplatform.education.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
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

/** A student's score on an assessment (0 … its max score). No row = not graded yet. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_score")
@IdClass(Score.Key.class)
public class Score {

    @Id
    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Id
    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private BigDecimal score;

    @Column(name = "entered_by", nullable = false)
    private UUID enteredBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID assessmentId;
        private UUID studentId;
    }
}
