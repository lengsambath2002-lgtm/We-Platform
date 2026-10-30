package com.businessplatform.education.domain;

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

/** An exam, quiz or assignment of a course in a term; {@code weight} is its share of the term result. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_assessment")
public class Assessment {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "term_id", nullable = false)
    private UUID termId;

    @Column(nullable = false)
    private String name;

    /** EXAM, QUIZ, ASSIGNMENT, PROJECT or ORAL. */
    @Column(nullable = false)
    private String kind;

    @Column(name = "held_on", nullable = false)
    private LocalDate heldOn;

    @Column(name = "max_score", nullable = false)
    private BigDecimal maxScore;

    @Column(nullable = false)
    private BigDecimal weight;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
